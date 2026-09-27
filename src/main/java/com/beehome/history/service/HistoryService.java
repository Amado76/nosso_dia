package com.beehome.history.service;

import com.beehome.dailyexecution.dto.ExecutionResponse;
import com.beehome.dailyexecution.service.DailyExecutionService;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.history.dto.*;
import com.beehome.history.exception.ReportException;
import com.beehome.reading.service.ReadingService;
import com.beehome.photorecord.dto.PhotoRecordResponse;
import com.beehome.photorecord.service.PhotoRecordService;
import com.beehome.shared.exception.InputException;
import com.beehome.study.dto.StudySessionResponse;
import com.beehome.study.service.StudyService;
import com.beehome.activity.service.ActivityService;
import com.beehome.activity.dto.ActivityRecordResponse;
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
    private final ReadingService reading;
    private final ActivityService activities;
    private final int maxPeriodDays;
    private final int maxDailyReportItems;

    public HistoryService(FamilyMemberService members, DailyExecutionService executions, StudyService studies,
            PhotoRecordService photos, ReadingService reading, ActivityService activities,
            @Value("${app.reports.max-period-days:366}") int maxPeriodDays,
            @Value("${app.reports.max-daily-items:500}") int maxDailyReportItems) {
        this.members = members; this.executions = executions; this.studies = studies; this.photos = photos;
        this.reading = reading;
        this.activities = activities;
        if (maxPeriodDays < 1) throw new IllegalArgumentException("app.reports.max-period-days must be positive");
        if (maxDailyReportItems < 1) throw new IllegalArgumentException("app.reports.max-daily-items must be positive");
        this.maxPeriodDays = maxPeriodDays;
        this.maxDailyReportItems = maxDailyReportItems;
    }

    private String child(UUID user, UUID family, UUID child) {
        var member = members.get(user, family, child);
        if (member.memberType() != MemberType.CHILD) throw FamilyMemberException.notFound();
        return member.name();
    }
    private void range(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= maxPeriodDays)
            throw new InputException();
    }

    @Transactional(readOnly = true)
    public HistoryDetail detail(UUID user, UUID family, UUID child, LocalDate date, int readingPage, int readingSize) {
        String childName = child(user, family, child);
        if (date == null) throw new InputException();
        var readingSessions = reading.listSessions(user, family, child, date, date, null, readingPage, readingSize);
        var sources = sources(user, family, child, date, date);
        return new HistoryDetail(date, child, childName, sources.routines.isEmpty() ? null : sources.routines.getFirst(),
                sources.studies, readingSessions.items().stream().map(session -> new HistoryDetail.Reading(
                        session.id(), session.childBookId(), session.book().id(), session.book().title(),
                        session.minutes(), session.pagesRead())).toList(),
                readingSessions.page(), readingSessions.size(), readingSessions.hasNext(), reportPhotos(sources.photos),
                sources.activities);
    }

    @Transactional(readOnly = true)
    public HistoryDetail reportDetail(UUID user, UUID family, UUID child, LocalDate date) {
        child(user, family, child);
        if (date == null) throw new InputException();
        long count = executions.historyCounts(user, family, child, date, date).stream()
                .mapToLong(day -> 1 + day.plannedItems()).sum()
                + studies.historyCounts(user, family, child, date, date).stream().mapToLong(StudyService.StudyDay::sessions).sum()
                + photos.historyCounts(user, family, child, date, date).stream().mapToLong(PhotoRecordService.PhotoDay::records).sum()
                + activities.historyCounts(user, family, child, date, date).stream().mapToLong(ActivityService.ActivityDay::records).sum()
                + reading.historyCounts(user, family, child, date, date).stream().mapToLong(ReadingService.ReadingDay::sessions).sum();
        if (count > maxDailyReportItems) throw ReportException.tooLarge();
        var first = detail(user, family, child, date, 0, 100);
        long loaded = (first.routine() == null ? 0 : 1L + first.routine().items().size())
                + first.studies().size() + first.photos().size() + first.extracurricularActivities().size();
        if (loaded + first.reading().size() > maxDailyReportItems) throw ReportException.tooLarge();
        if (!first.readingHasNext()) return first;
        var all = new ArrayList<>(first.reading());
        int page = 1;
        boolean more = true;
        while (more) {
            var next = reading.listSessions(user, family, child, date, date, null, page, 100);
            next.items().forEach(session -> all.add(new HistoryDetail.Reading(session.id(), session.childBookId(),
                    session.book().id(), session.book().title(), session.minutes(), session.pagesRead())));
            if (loaded + all.size() > maxDailyReportItems) throw ReportException.tooLarge();
            more = next.hasNext();
            page++;
        }
        return new HistoryDetail(first.date(), first.childId(), first.childName(), first.routine(),
                first.studies(), all, 0, 100, false, first.photos(), first.extracurricularActivities());
    }

    private static List<HistoryDetail.Photo> reportPhotos(List<PhotoRecordResponse> records) {
        var result = new ArrayList<HistoryDetail.Photo>();
        int remaining = 4;
        for (var record : records) {
            if (remaining == 0) break;
            var media = record.media().stream().limit(remaining)
                    .map(item -> new HistoryDetail.Photo.Media(item.id(), item.position())).toList();
            if (media.isEmpty()) continue;
            result.add(new HistoryDetail.Photo(record.id(), record.date(), record.description(), record.tags(), media));
            remaining -= media.size();
        }
        return result;
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
        var readingDates = new HashSet<>(reading.historyDates(user, family, child, start, end));
        var activityDates = new HashSet<>(activities.historyDates(user, family, child, start, end));
        var dates = new TreeSet<LocalDate>();
        dates.addAll(routineDates); dates.addAll(studyDates); dates.addAll(photoDates); dates.addAll(readingDates); dates.addAll(activityDates);
        return new HistoryCalendar(year, month, dates.stream().map(date ->
                new HistoryCalendar.Day(date, routineDates.contains(date), studyDates.contains(date), readingDates.contains(date),
                        photoDates.contains(date), activityDates.contains(date))).toList());
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
        var readingDays = reading.historyCounts(user, family, child, from, to).stream()
                .collect(Collectors.toMap(ReadingService.ReadingDay::date, r -> r));
        var activityDays = activities.historyCounts(user, family, child, from, to).stream()
                .collect(Collectors.toMap(ActivityService.ActivityDay::date, r -> r));
        var dates = new TreeSet<LocalDate>(Comparator.reverseOrder());
        dates.addAll(routines.keySet()); dates.addAll(studyDays.keySet()); dates.addAll(photoDays.keySet()); dates.addAll(readingDays.keySet()); dates.addAll(activityDays.keySet());
        var selected = dates.stream().skip((long) page * size).limit(size + 1L).toList();
        var items = selected.stream().limit(size).map(date -> {
            var routine = routines.get(date);
            var sessions = studyDays.get(date);
            var records = photoDays.get(date);
            var readingDay = readingDays.get(date);
            var activityDay = activityDays.get(date);
            return new HistoryPage.Day(date, routine != null, routine == null ? 0 : Math.toIntExact(routine.plannedItems()),
                    routine == null ? 0 : Math.toIntExact(routine.completedItems()),
                    sessions == null ? 0 : Math.toIntExact(sessions.sessions()),
                    sessions == null ? 0 : sessions.durationSeconds(),
                    readingDay == null ? 0 : readingDay.sessions(),
                    records == null ? 0 : Math.toIntExact(records.records()),
                    records == null ? 0 : Math.toIntExact(records.images()),
                    activityDay == null ? 0 : activityDay.records(),
                    activityDay == null ? 0 : activityDay.minutes());
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
        var readingSummary = reading.summary(user, family, child, from, to);
        var activitySummary = activities.summary(user, family, child, from, to);
        var subjects = studySummary.subjects().stream()
                .map(subject -> new HistoryReport.Subject(subject.subjectId(), subject.sessionCount(), minutes(subject.durationSeconds()))).toList();
        return new HistoryReport(child, from, to,
                new HistoryReport.Routine(routineDays.size(),
                        routineDays.stream().mapToLong(DailyExecutionService.RoutineDay::plannedItems).sum(),
                        routineDays.stream().mapToLong(DailyExecutionService.RoutineDay::completedItems).sum()),
                new HistoryReport.Studies(studyDays.stream().mapToLong(StudyService.StudyDay::completedSessions).sum(),
                        minutes(studySummary.totalDurationSeconds()), subjects),
                new HistoryReport.Reading(readingSummary.sessions(), readingSummary.totalMinutes(),
                        readingSummary.pagesRead(), readingSummary.books(), readingSummary.booksCompleted()),
                new HistoryReport.Photos(photoDays.stream().mapToLong(PhotoRecordService.PhotoDay::records).sum(),
                        photoDays.stream().mapToLong(PhotoRecordService.PhotoDay::images).sum()),
                new HistoryReport.ExtracurricularActivities(activitySummary.stream().mapToLong(ActivityService.ActivityTotal::records).sum(),
                        activitySummary.stream().mapToLong(ActivityService.ActivityTotal::durationMinutes).sum(),
                        activitySummary.stream().map(row -> new HistoryReport.Activity(row.activityId(), row.records(), row.durationMinutes())).toList()));
    }

    private Sources sources(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        return new Sources(executions.historyRange(user, family, child, from, to),
                studies.historyRange(user, family, child, from, to), photos.historyRange(user, family, child, from, to),
                activities.historyRange(user, family, child, from, to));
    }
    private static BigDecimal minutes(long seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
    private record Sources(List<ExecutionResponse> routines, List<StudySessionResponse> studies,
            List<PhotoRecordResponse> photos, List<ActivityRecordResponse> activities) {}
}
