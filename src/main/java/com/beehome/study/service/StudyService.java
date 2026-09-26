package com.beehome.study.service;

import com.beehome.dailyexecution.service.DailyExecutionService;
import com.beehome.family.service.*;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.shared.exception.InputException;
import com.beehome.study.dto.*;
import com.beehome.study.entity.*;
import com.beehome.study.exception.StudyException;
import com.beehome.study.repository.*;
import com.beehome.tag.dto.TagSummary;
import com.beehome.tag.service.TagService;
import java.time.*;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyService {
    private final StudySubjectRepository subjects;
    private final StudySessionRepository sessions;
    private final StudySessionTagRepository sessionTags;
    private final TagService tags;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final FamilyService families;
    private final DailyExecutionService executions;
    private final Clock clock;
    public StudyService(StudySubjectRepository subjects, StudySessionRepository sessions, StudySessionTagRepository sessionTags, TagService tags,
            FamilyAuthorizationService authorization, FamilyMemberService members, FamilyService families,
            DailyExecutionService executions, Clock clock) {
        this.subjects = subjects; this.sessions = sessions; this.sessionTags = sessionTags; this.tags = tags; this.authorization = authorization;
        this.members = members; this.families = families; this.executions = executions; this.clock = clock;
    }
    private void editor(UUID user, UUID family) { authorization.requireEditor(authorization.requireMembership(user, family)); }
    private StudySubject subject(UUID family, UUID id) {
        return subjects.findByFamilyIdAndId(family, id).orElseThrow(StudyException::subjectMissing);
    }
    private StudySubject lockedSubject(UUID family, UUID id) {
        return subjects.lockByFamilyIdAndId(family, id).orElseThrow(StudyException::subjectMissing);
    }
    @Transactional
    public StudySubjectResponse createSubject(UUID user, UUID family, StudySubjectRequest body) {
        families.lockForWrite(user, family);
        if (subjects.countByFamilyId(family) >= 1000) throw new InputException();
        var subject = new StudySubject(family, body.name(), body.color(), body.sortOrder() == null ? 0 : body.sortOrder(), clock.instant());
        return StudySubjectResponse.from(saveSubject(subject));
    }
    @Transactional(readOnly = true)
    public List<StudySubjectResponse> listSubjects(UUID user, UUID family, boolean includeInactive) {
        authorization.requireMembership(user, family);
        return subjects.list(family, includeInactive).stream().map(StudySubjectResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public StudySubjectResponse getSubject(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user, family); return StudySubjectResponse.from(subject(family, id));
    }
    @Transactional
    public StudySubjectResponse patchSubject(UUID user, UUID family, UUID id, StudySubjectRequest body) {
        editor(user, family); var subject = lockedSubject(family, id);
        body.validatePatch();
        subject.edit(body.fields().contains("name") ? body.name() : subject.getName(),
                body.fields().contains("color") ? body.color() : subject.getColor(),
                body.fields().contains("sortOrder") ? body.sortOrder() : subject.getSortOrder(), clock.instant());
        return StudySubjectResponse.from(saveSubject(subject));
    }
    @Transactional
    public StudySubjectResponse activeSubject(UUID user, UUID family, UUID id, boolean active) {
        editor(user, family); var subject = lockedSubject(family, id); subject.active(active, clock.instant());
        return StudySubjectResponse.from(subject);
    }
    private StudySubject saveSubject(StudySubject subject) {
        try { return subjects.saveAndFlush(subject); }
        catch (DataIntegrityViolationException e) {
            if (constraint(e, "study_subjects_family_name_unique")) throw StudyException.duplicate();
            throw e;
        }
    }
    private static boolean constraint(Throwable error, String name) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof ConstraintViolationException c && name.equals(c.getConstraintName())) return true;
        return false;
    }
    private StudySubject linkedSubject(UUID family, UUID id) {
        if (id == null) return null;
        var subject = subject(family, id);
        if (!subject.isActive()) throw StudyException.subjectMissing();
        return subject;
    }
    private void linkedItem(UUID family, UUID member, UUID item) {
        if (item != null && !executions.ownsItem(family, member, item)) throw StudyException.itemMissing();
    }
    private StudySessionResponse saveSession(StudySession session) {
        try { return StudySessionResponse.from(sessions.saveAndFlush(session)); }
        catch (DataIntegrityViolationException e) {
            if (constraint(e, "study_sessions_one_running")) throw StudyException.conflict();
            throw e;
        }
    }
    private void replaceSessionTags(UUID user,UUID family,UUID sessionId,List<UUID> ids) {
        tags.requireTags(user,family,ids);
        sessionTags.deleteBySessionId(sessionId);
        sessionTags.flush();
        sessionTags.saveAll(ids.stream().map(id -> new StudySessionTag(family,sessionId,id)).toList());
    }
    private List<StudySessionResponse> sessionResponses(UUID family,List<StudySession> rows) {
        if(rows.isEmpty()) return List.of();
        var links=sessionTags.findBySessionIdIn(rows.stream().map(StudySession::getId).toList());
        Map<UUID,TagSummary> summaries=tags.summaries(family,links.stream().map(StudySessionTag::getTagId).collect(java.util.stream.Collectors.toSet()));
        Map<UUID,List<TagSummary>> bySession=new HashMap<>();
        for(var link:links) bySession.computeIfAbsent(link.getSessionId(),_ -> new ArrayList<>()).add(summaries.get(link.getTagId()));
        return rows.stream().map(row -> StudySessionResponse.from(row,bySession.getOrDefault(row.getId(),List.of()).stream()
                .sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList())).toList();
    }
    private StudySessionResponse sessionResponse(StudySession row) { return sessionResponses(row.getFamilyId(),List.of(row)).getFirst(); }
    @Transactional
    public StudySessionResponse start(UUID user, UUID family, UUID member, StudySessionRequest body) {
        members.requireActive(user, family, member, true); body.validateStart();
        tags.requireTags(user,family,body.tagIds());
        var subject = linkedSubject(family, body.subjectId());
        UUID item = body.dailyExecutionItemId(); linkedItem(family, member, item);
        var now = clock.instant();
        var date = LocalDate.ofInstant(now, ZoneId.of(families.get(user, family).timezone()));
        var session=StudySession.timer(family, member, subject == null ? null : subject.getId(),
                subject == null ? null : subject.getName(), item, date, body.title(), body.notes(), user, now);
        saveSession(session); replaceSessionTags(user,family,session.getId(),body.tagIds()); return sessionResponse(session);
    }
    @Transactional
    public StudySessionResponse manual(UUID user, UUID family, UUID member, StudySessionRequest body) {
        members.requireActive(user, family, member, true); body.validateManual();
        tags.requireTags(user,family,body.tagIds());
        var subject = linkedSubject(family, body.subjectId());
        UUID item = body.dailyExecutionItemId(); linkedItem(family, member, item);
        var session=StudySession.manual(family, member, subject == null ? null : subject.getId(),
                subject == null ? null : subject.getName(), item, body.date(), body.title(), body.notes(), body.durationSeconds(), user, clock.instant());
        saveSession(session); replaceSessionTags(user,family,session.getId(),body.tagIds()); return sessionResponse(session);
    }
    private StudySession session(UUID family, UUID member, UUID id) {
        var session = sessions.findByFamilyIdAndFamilyMemberIdAndId(family, member, id).orElseThrow(StudyException::sessionMissing);
        if (session.getStatus() == StudyStatus.VOIDED) throw StudyException.sessionMissing();
        return session;
    }
    @Transactional(readOnly = true)
    public StudySessionResponse get(UUID user, UUID family, UUID member, UUID id) {
        members.get(user, family, member); return sessionResponse(session(family, member, id));
    }
    @Transactional(readOnly = true)
    public StudySessionResponse current(UUID user, UUID family, UUID member) {
        members.get(user, family, member);
        return sessions.findByFamilyIdAndFamilyMemberIdAndStatus(family, member, StudyStatus.RUNNING)
                .map(this::sessionResponse).orElse(null);
    }
    @Transactional
    public StudySessionResponse command(UUID user, UUID family, UUID member, UUID id, String command) {
        members.get(user, family, member); var session = session(family, member, id);
        var now = clock.instant();
        switch (command) {
            case "pause" -> session.pause(now);
            case "resume" -> session.resume(now);
            case "finish" -> session.finish(now);
            default -> throw new InputException();
        }
        saveSession(session); return sessionResponse(session);
    }
    @Transactional
    public StudySessionResponse correct(UUID user, UUID family, UUID member, UUID id, StudySessionRequest body) {
        members.get(user, family, member); editor(user, family); var session = session(family, member, id);
        body.validatePatch();
        if(body.fields().contains("tagIds")) tags.requireTags(user,family,body.tagIds());
        UUID subjectId = body.fields().contains("subjectId") ? body.subjectId() : session.getSubjectId();
        var subject = body.fields().contains("subjectId") ? linkedSubject(family, subjectId) : null;
        UUID item = body.fields().contains("dailyExecutionItemId") ? body.dailyExecutionItemId() : session.getDailyExecutionItemId();
        linkedItem(family, member, item);
        session.correct(subjectId, body.fields().contains("subjectId") ? subject == null ? null : subject.getName() : session.getSubjectNameSnapshot(),
                item, body.fields().contains("date") ? body.date() : null,
                body.fields().contains("title") ? body.title() : session.getTitle(),
                body.fields().contains("notes") ? body.notes() : session.getNotes(),
                body.fields().contains("durationSeconds") ? body.durationSeconds() : null, clock.instant());
        saveSession(session);
        if(body.fields().contains("tagIds")) replaceSessionTags(user,family,id,body.tagIds());
        return sessionResponse(session);
    }
    @Transactional
    public StudySessionResponse voidSession(UUID user, UUID family, UUID member, UUID id) {
        members.get(user, family, member); editor(user, family);
        var session = session(family, member, id); session.voidSession(clock.instant()); saveSession(session); return sessionResponse(session);
    }
    @Transactional(readOnly = true)
    public StudySessionPage history(UUID user, UUID family, UUID member, LocalDate from, LocalDate to, UUID subject, List<UUID> tagIds, int page, int size) {
        members.get(user, family, member); validateRange(from, to);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 1) throw new InputException();
        if (subject != null) subject(family, subject);
        tags.requireTags(user,family,tagIds);
        var slice = tagIds.isEmpty() ? sessions.history(family, member, from, to, subject, PageRequest.of(page, size))
                : sessions.historyTagged(family, member, from, to, subject, tagIds, tagIds.size(), PageRequest.of(page, size));
        return new StudySessionPage(sessionResponses(family,slice.getContent()), page, size, slice.hasNext());
    }
    @Transactional(readOnly = true)
    public StudySummary summary(UUID user, UUID family, UUID member, LocalDate from, LocalDate to) {
        members.get(user, family, member); validateRange(from, to);
        var rows = sessions.summary(family, member, from, to);
        long total = rows.stream().mapToLong(StudySessionRepository.SummaryRow::getSeconds).sum();
        var bySubject = rows.stream().filter(r -> r.getSubjectId() != null)
                .map(r -> new StudySummary.Subject(r.getSubjectId(), r.getSeconds(), r.getCount()))
                .sorted(Comparator.comparing(s -> s.subjectId().toString())).toList();
        return new StudySummary(from, to, total, bySubject);
    }
    @Transactional(readOnly = true)
    public List<LocalDate> historyDates(UUID user, UUID family, UUID member, LocalDate from, LocalDate to) {
        members.get(user, family, member);
        return sessions.dates(family, member, from, to);
    }
    @Transactional(readOnly = true)
    public List<StudyDay> historyCounts(UUID user, UUID family, UUID member, LocalDate from, LocalDate to) {
        members.get(user, family, member);
        return sessions.dayCounts(family, member, from, to).stream().map(row ->
                new StudyDay(row.getDate(), row.getSessions(), row.getCompletedSessions(), row.getSeconds())).toList();
    }
    public record StudyDay(LocalDate date, long sessions, long completedSessions, long durationSeconds) {}
    @Transactional(readOnly = true)
    public List<StudySessionResponse> historyRange(UUID user, UUID family, UUID member, LocalDate from, LocalDate to) {
        members.get(user, family, member);
        return sessions.range(family, member, from, to).stream().map(StudySessionResponse::from).toList();
    }
    private static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new InputException();
    }
}
