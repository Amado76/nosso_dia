package com.beehome.history.service;

import com.beehome.dailyexecution.dto.ExecutionResponse;
import com.beehome.dailyexecution.service.DailyExecutionService;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.history.dto.*;
import com.beehome.photorecord.dto.PhotoRecordResponse;
import com.beehome.photorecord.service.PhotoRecordService;
import com.beehome.shared.exception.InputException;
import com.beehome.study.dto.StudySessionResponse;
import com.beehome.study.service.StudyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {
    private final FamilyMemberService members;
    private final DailyExecutionService executions;
    private final StudyService studies;
    private final PhotoRecordService photos;
    private final int maxPeriodDays;

    public HistoryService(FamilyMemberService members, DailyExecutionService executions, StudyService studies,
            PhotoRecordService photos, @Value("${app.reports.max-period-days:366}") int maxPeriodDays) {
        this.members = members; this.executions = executions; this.studies = studies; this.photos = photos;
        if (maxPeriodDays < 1) throw new IllegalArgumentException("app.reports.max-period-days must be positive");
        this.maxPeriodDays = maxPeriodDays;
    }

    private void child(UUID user, UUID family, UUID child) {
        if (members.get(user, family, child).memberType() != MemberType.CHILD) throw FamilyMemberException.notFound();
    }
    private void range(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= maxPeriodDays)
            throw new InputException();
    }

    @Transactional(readOnly = true)
    public HistoryDetail detail(UUID user, UUID family, UUID child, LocalDate date) {
        child(user, family, child);
        var sources = sources(user, family, child, date, date);
        return new HistoryDetail(date, child, sources.routines.isEmpty() ? null : sources.routines.getFirst(),
                sources.studies, List.of(), sources.photos.stream().map(record -> new HistoryDetail.Photo(record.id(),
                        record.media().stream().map(media -> new HistoryDetail.Photo.Media(media.id(), media.position())).toList())).toList());
    }

    @Transactional(readOnly = true)
    public HistoryCalendar calendar(UUID user, UUID family, UUID child, Integer year, Integer month) {
        child(user, family, child);
        if (year == null || month == null || year < 1 || year > 9999 || month < 1 || month > 12) throw new InputException();
        var start = LocalDate.of(year, month, 1);
        var end = start.withDayOfMonth(start.lengthOfMonth());
        var routineDates = new HashSet<>(executions.historyDates(user, family, child, start, end));
        var studyDates = new HashSet<>(studies.historyDates(user, family, child, start, end));
        var photoDates = new HashSet<>(photos.historyDates(user, family, child, start, end));
        var dates = new TreeSet<LocalDate>();
        dates.addAll(routineDates); dates.addAll(studyDates); dates.addAll(photoDates);
        return new HistoryCalendar(year, month, dates.stream().map(date ->
                new HistoryCalendar.Day(date, routineDates.contains(date), studyDates.contains(date), false,
                        photoDates.contains(date))).toList());
    }

    @Transactional(readOnly = true)
    public HistoryPage history(UUID user, UUID family, UUID child, LocalDate from, LocalDate to, int page, int size) {
        child(user, family, child); range(from, to);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 1) throw new InputException();
        var routines = executions.historyCounts(user, family, child, from, to).stream()
                .collect(Collectors.toMap(DailyExecutionService.RoutineDay::date, r -> r));
        var studyDays = studies.historyCounts(user, family, child, from, to).stream()
                .collect(Collectors.toMap(StudyService.StudyDay::date, s -> s));
        var photoDays = photos.historyCounts(user, family, child, from, to).stream()
                .collect(Collectors.toMap(PhotoRecordService.PhotoDay::date, p -> p));
        var dates = new TreeSet<LocalDate>(Comparator.reverseOrder());
        dates.addAll(routines.keySet()); dates.addAll(studyDays.keySet()); dates.addAll(photoDays.keySet());
        var selected = dates.stream().skip((long) page * size).limit(size + 1L).toList();
        var items = selected.stream().limit(size).map(date -> {
            var routine = routines.get(date);
            var sessions = studyDays.get(date);
            var records = photoDays.get(date);
            return new HistoryPage.Day(date, routine != null, routine == null ? 0 : Math.toIntExact(routine.plannedItems()),
                    routine == null ? 0 : Math.toIntExact(routine.completedItems()),
                    sessions == null ? 0 : Math.toIntExact(sessions.sessions()),
                    sessions == null ? 0 : sessions.durationSeconds(),
                    records == null ? 0 : Math.toIntExact(records.records()),
                    records == null ? 0 : Math.toIntExact(records.images()));
        }).toList();
        return new HistoryPage(items, page, size, selected.size() > size);
    }

    @Transactional(readOnly = true)
    public HistoryReport report(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user, family, child); range(from, to);
        var routineDays = executions.historyCounts(user, family, child, from, to);
        var studyDays = studies.historyCounts(user, family, child, from, to);
        var photoDays = photos.historyCounts(user, family, child, from, to);
        var studySummary = studies.summary(user, family, child, from, to);
        var subjects = studySummary.subjects().stream()
                .map(subject -> new HistoryReport.Subject(subject.subjectId(), minutes(subject.durationSeconds()))).toList();
        return new HistoryReport(child, from, to,
                new HistoryReport.Routine(routineDays.size(),
                        routineDays.stream().mapToLong(DailyExecutionService.RoutineDay::plannedItems).sum(),
                        routineDays.stream().mapToLong(DailyExecutionService.RoutineDay::completedItems).sum()),
                new HistoryReport.Studies(studyDays.stream().mapToLong(StudyService.StudyDay::completedSessions).sum(),
                        minutes(studySummary.totalDurationSeconds()), subjects),
                new HistoryReport.Reading(0, 0, 0),
                new HistoryReport.Photos(photoDays.stream().mapToLong(PhotoRecordService.PhotoDay::records).sum(),
                        photoDays.stream().mapToLong(PhotoRecordService.PhotoDay::images).sum()));
    }

    private Sources sources(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        return new Sources(executions.historyRange(user, family, child, from, to),
                studies.historyRange(user, family, child, from, to), photos.historyRange(user, family, child, from, to));
    }
    private static BigDecimal minutes(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
    private record Sources(List<ExecutionResponse> routines, List<StudySessionResponse> studies,
            List<PhotoRecordResponse> photos) {}
}
