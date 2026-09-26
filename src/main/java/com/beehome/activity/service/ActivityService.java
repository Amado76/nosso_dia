package com.beehome.activity.service;

import com.beehome.activity.dto.*;
import com.beehome.activity.entity.*;
import com.beehome.activity.exception.ActivityException;
import com.beehome.activity.repository.*;
import com.beehome.family.service.*;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.shared.exception.InputException;
import com.beehome.tag.dto.TagSummary;
import com.beehome.tag.service.TagService;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {
    private final ExtracurricularActivityRepository activities;
    private final ExtracurricularRecordRepository records;
    private final ExtracurricularRecordTagRepository recordTags;
    private final FamilyAuthorizationService authorization;
    private final FamilyService families;
    private final FamilyMemberService members;
    private final TagService tags;
    private final Clock clock;
    public ActivityService(ExtracurricularActivityRepository activities, ExtracurricularRecordRepository records,
            ExtracurricularRecordTagRepository recordTags, FamilyAuthorizationService authorization,
            FamilyService families, FamilyMemberService members, TagService tags, Clock clock) {
        this.activities = activities; this.records = records; this.recordTags = recordTags;
        this.authorization = authorization; this.families = families; this.members = members; this.tags = tags; this.clock = clock;
    }
    private void editor(UUID user, UUID family) { authorization.requireEditor(authorization.requireMembership(user, family)); }
    private void child(UUID user, UUID family, UUID child, boolean write) {
        var member = write ? members.requireActive(user, family, child, true) : members.get(user, family, child);
        if (member.memberType() != MemberType.CHILD) throw FamilyMemberException.notFound();
    }
    private ExtracurricularActivity activity(UUID family, UUID id) {
        return activities.findByFamilyIdAndId(family, id).orElseThrow(ActivityException::missing);
    }
    private ExtracurricularActivity activeActivity(UUID family, UUID id) {
        var found = activity(family, id);
        if (!found.isActive()) throw ActivityException.missing();
        return found;
    }
    private static boolean constraint(Throwable error, String name) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof ConstraintViolationException violation && name.equals(violation.getConstraintName())) return true;
        return false;
    }
    private ActivityResponse saveActivity(ExtracurricularActivity activity) {
        try { return ActivityResponse.from(activities.saveAndFlush(activity)); }
        catch (DataIntegrityViolationException e) {
            if (constraint(e, "extracurricular_activities_family_name_unique")) throw ActivityException.duplicate();
            throw e;
        }
    }
    @Transactional
    public ActivityResponse createActivity(UUID user, UUID family, ActivityRequest body) {
        families.lockForWrite(user, family);
        if (body == null || activities.countByFamilyId(family) >= 1000) throw new InputException();
        return saveActivity(new ExtracurricularActivity(family, body.name(), body.description(),
                body.sortOrder() == null ? 0 : body.sortOrder(), clock.instant()));
    }
    @Transactional(readOnly = true)
    public List<ActivityResponse> listActivities(UUID user, UUID family, boolean includeInactive) {
        authorization.requireMembership(user, family);
        return activities.list(family, includeInactive).stream().map(ActivityResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public ActivityResponse getActivity(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user, family); return ActivityResponse.from(activity(family, id));
    }
    @Transactional
    public ActivityResponse patchActivity(UUID user, UUID family, UUID id, ActivityRequest body) {
        editor(user, family); if (body == null) throw new InputException(); body.validatePatch();
        var activity = activities.lock(family, id).orElseThrow(ActivityException::missing);
        activity.edit(body.fields().contains("name") ? body.name() : activity.getName(),
                body.fields().contains("description") ? body.description() : activity.getDescription(),
                body.fields().contains("sortOrder") ? body.sortOrder() : activity.getSortOrder(), clock.instant());
        return saveActivity(activity);
    }
    @Transactional
    public ActivityResponse activeActivity(UUID user, UUID family, UUID id, boolean active) {
        editor(user, family);
        var activity = activities.lock(family, id).orElseThrow(ActivityException::missing);
        activity.active(active, clock.instant()); return ActivityResponse.from(activity);
    }
    private ExtracurricularRecord record(UUID family, UUID child, UUID id) {
        return records.findByFamilyIdAndChildIdAndId(family, child, id).orElseThrow(ActivityException::recordMissing);
    }
    private void replaceTags(UUID user, UUID family, UUID record, List<UUID> ids) {
        tags.requireTags(user, family, ids);
        recordTags.deleteForRecord(record); recordTags.flush();
        recordTags.saveAll(ids.stream().map(id -> new ExtracurricularRecordTag(family, record, id)).toList());
    }
    private List<ActivityRecordResponse> responses(UUID family, List<ExtracurricularRecord> rows) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(ExtracurricularRecord::getId).toList();
        var links = recordTags.findByRecordIdIn(ids);
        var names = activities.findForIds(family, rows.stream().map(ExtracurricularRecord::getActivityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ExtracurricularActivity::getId, ExtracurricularActivity::getName));
        var tagNames = tags.summaries(family, links.stream().map(ExtracurricularRecordTag::getTagId).collect(Collectors.toSet()));
        Map<UUID,List<TagSummary>> byRecord = new HashMap<>();
        for (var link : links) byRecord.computeIfAbsent(link.getRecordId(), _ -> new ArrayList<>()).add(tagNames.get(link.getTagId()));
        return rows.stream().map(row -> ActivityRecordResponse.from(row,
                new ActivityRecordResponse.Activity(row.getActivityId(), names.get(row.getActivityId())),
                byRecord.getOrDefault(row.getId(), List.of()).stream().filter(Objects::nonNull)
                        .sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList())).toList();
    }
    @Transactional
    public ActivityRecordResponse createRecord(UUID user, UUID family, UUID child, ActivityRecordRequest body) {
        child(user, family, child, true); if (body == null) throw new InputException(); body.validateCreate();
        activeActivity(family, body.activityId()); tags.requireTags(user, family, body.tagIds());
        var record = records.saveAndFlush(new ExtracurricularRecord(family, child, user, body.activityId(), body.date(),
                body.topic(), body.durationMinutes(), body.description(), body.comments(), body.material(),
                body.startPage(), body.endPage(), clock.instant()));
        replaceTags(user, family, record.getId(), body.tagIds());
        return responses(family, List.of(record)).getFirst();
    }
    @Transactional(readOnly = true)
    public ActivityRecordResponse getRecord(UUID user, UUID family, UUID child, UUID id) {
        child(user, family, child, false); return responses(family, List.of(record(family, child, id))).getFirst();
    }
    @Transactional
    public ActivityRecordResponse patchRecord(UUID user, UUID family, UUID child, UUID id, ActivityRecordRequest body) {
        child(user, family, child, true); if (body == null) throw new InputException(); body.validatePatch();
        var record = record(family, child, id);
        UUID activityId = body.fields().contains("activityId") ? body.activityId() : record.getActivityId();
        if (!activityId.equals(record.getActivityId())) activeActivity(family, activityId);
        if (body.fields().contains("tagIds")) tags.requireTags(user, family, body.tagIds());
        record.edit(activityId, body.fields().contains("date") ? body.date() : record.getDate(),
                body.fields().contains("topic") ? body.topic() : record.getTopic(),
                body.fields().contains("durationMinutes") ? body.durationMinutes() : record.getDurationMinutes(),
                body.fields().contains("description") ? body.description() : record.getDescription(),
                body.fields().contains("comments") ? body.comments() : record.getComments(),
                body.fields().contains("material") ? body.material() : record.getMaterial(),
                body.fields().contains("startPage") ? body.startPage() : record.getStartPage(),
                body.fields().contains("endPage") ? body.endPage() : record.getEndPage(), clock.instant());
        records.flush();
        if (body.fields().contains("tagIds")) replaceTags(user, family, id, body.tagIds());
        return responses(family, List.of(record)).getFirst();
    }
    @Transactional
    public void deleteRecord(UUID user, UUID family, UUID child, UUID id) {
        child(user, family, child, true); var record = record(family, child, id);
        recordTags.deleteForRecord(id); records.delete(record);
    }
    @Transactional(readOnly = true)
    public ActivityRecordPage listRecords(UUID user, UUID family, UUID child, LocalDate from, LocalDate to, int page, int size) {
        child(user, family, child, false);
        if (from == null || to == null || from.isAfter(to) || page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE - 1) throw new InputException();
        var slice = records.history(family, child, from, to, PageRequest.of(page, size));
        return new ActivityRecordPage(responses(family, slice.getContent()), page, size, slice.hasNext());
    }
    @Transactional(readOnly = true)
    public List<ActivityRecordResponse> historyRange(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user, family, child, false); return responses(family, records.range(family, child, from, to));
    }
    @Transactional(readOnly = true)
    public List<LocalDate> historyDates(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user, family, child, false); return records.dates(family, child, from, to);
    }
    @Transactional(readOnly = true)
    public List<ActivityDay> historyCounts(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user, family, child, false);
        return records.dayCounts(family, child, from, to).stream().map(row ->
                new ActivityDay(row.getDate(), row.getRecords(), row.getMinutes())).toList();
    }
    public record ActivityDay(LocalDate date, long records, long minutes) {}
    @Transactional(readOnly = true)
    public List<ActivityTotal> summary(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user, family, child, false);
        return records.summary(family, child, from, to).stream().map(row ->
                new ActivityTotal(row.getActivityId(), row.getRecords(), row.getMinutes()))
                .sorted(Comparator.comparing(ActivityTotal::activityId)).toList();
    }
    public record ActivityTotal(UUID activityId, long records, long durationMinutes) {}
}
