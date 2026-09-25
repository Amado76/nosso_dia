package com.beehome.history.service;

import com.beehome.dailyexecution.dto.*;
import com.beehome.dailyexecution.entity.*;
import com.beehome.dailyexecution.service.DailyExecutionService;
import com.beehome.familymember.dto.FamilyMemberResponse;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.photorecord.dto.PhotoRecordResponse;
import com.beehome.photorecord.service.PhotoRecordService;
import com.beehome.study.dto.StudySessionResponse;
import com.beehome.study.entity.*;
import com.beehome.study.service.StudyService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryServiceTests {
    private final UUID user = UUID.randomUUID(), family = UUID.randomUUID(), child = UUID.randomUUID();
    private final FamilyMemberService members = mock(FamilyMemberService.class);
    private final DailyExecutionService executions = mock(DailyExecutionService.class);
    private final StudyService studies = mock(StudyService.class);
    private final PhotoRecordService photos = mock(PhotoRecordService.class);
    private final com.beehome.reading.service.ReadingService reading = mock(com.beehome.reading.service.ReadingService.class);
    private final HistoryService history = new HistoryService(members, executions, studies, photos, reading, 366);

    private void child() {
        when(members.get(user, family, child)).thenReturn(new FamilyMemberResponse(child, family, "Child",
                MemberType.CHILD, null, null, null, false, true, Instant.EPOCH, Instant.EPOCH, java.util.Map.of()));
    }
    @Test void rejectsPeriodBeyondInclusiveLimitBeforeLoadingSources() {
        child();
        assertThatThrownBy(() -> history.report(user, family, child, LocalDate.parse("2025-01-01"),
                LocalDate.parse("2026-01-02"))).isInstanceOf(com.beehome.shared.exception.InputException.class);
        verifyNoInteractions(executions, studies, photos, reading);
    }
    @Test void aggregatesOnlyCompletedStudyMinutesAndExcludesCancelledRoutineItems() {
        child(); LocalDate date = LocalDate.parse("2026-09-21");
        UUID subject = UUID.randomUUID();
        when(executions.historyCounts(user, family, child, date, date))
                .thenReturn(List.of(new DailyExecutionService.RoutineDay(date, 1, 1)));
        when(studies.historyCounts(user, family, child, date, date))
                .thenReturn(List.of(new StudyService.StudyDay(date, 2, 1, 90)));
        when(studies.summary(user, family, child, date, date))
                .thenReturn(new com.beehome.study.dto.StudySummary(date, date, 90,
                        List.of(new com.beehome.study.dto.StudySummary.Subject(subject, 90, 1))));
        when(photos.historyCounts(user, family, child, date, date))
                .thenReturn(List.of(new PhotoRecordService.PhotoDay(date, 1, 1)));
        when(reading.summary(user, family, child, date, date))
                .thenReturn(new com.beehome.reading.dto.ReadingSummary(date, date, 0, 0, 0, 0, 0));
        var report = history.report(user, family, child, date, date);
        assertThat(report.routine().plannedItems()).isEqualTo(1);
        assertThat(report.routine().completedItems()).isEqualTo(1);
        assertThat(report.studies().sessions()).isEqualTo(1);
        assertThat(report.studies().totalMinutes()).isEqualByComparingTo(new BigDecimal("1.50"));
        assertThat(report.studies().subjects()).containsExactly(new com.beehome.history.dto.HistoryReport.Subject(subject, new BigDecimal("1.50")));
        assertThat(report.photos().images()).isEqualTo(1);
    }
}
