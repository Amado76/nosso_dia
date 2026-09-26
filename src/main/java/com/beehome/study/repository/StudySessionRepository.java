package com.beehome.study.repository;

import com.beehome.study.entity.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {
    Optional<StudySession> findByFamilyIdAndFamilyMemberIdAndId(UUID family, UUID member, UUID id);
    Optional<StudySession> findByFamilyIdAndFamilyMemberIdAndStatus(UUID family, UUID member, StudyStatus status);
    @Query("select s from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status <> 'VOIDED' and s.date between :from and :to and (:subject is null or s.subjectId = :subject) order by s.date desc, s.startedAt desc nulls last, s.id desc")
    Slice<StudySession> history(UUID family, UUID member, LocalDate from, LocalDate to, UUID subject, Pageable page);
    @Query("select s from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status <> 'VOIDED' " +
            "and s.date between :from and :to and (:subject is null or s.subjectId = :subject) " +
            "and s.id in (select st.sessionId from StudySessionTag st where st.tagId in :tagIds group by st.sessionId having count(st.tagId) = :tagCount) " +
            "order by s.date desc, s.startedAt desc nulls last, s.id desc")
    Slice<StudySession> historyTagged(UUID family, UUID member, LocalDate from, LocalDate to, UUID subject,
            Collection<UUID> tagIds, long tagCount, Pageable page);
    @Query("select s from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status <> 'VOIDED' and s.date between :from and :to order by s.date desc, s.id desc")
    List<StudySession> range(UUID family, UUID member, LocalDate from, LocalDate to);
    @Query("select distinct s.date from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status <> 'VOIDED' and s.date between :from and :to order by s.date")
    List<LocalDate> dates(UUID family, UUID member, LocalDate from, LocalDate to);
    @Query("select s.date as date, count(s) as sessions, " +
            "sum(case when s.status = 'COMPLETED' then 1 else 0 end) as completedSessions, " +
            "sum(case when s.status = 'COMPLETED' then s.accumulatedDurationSeconds else 0 end) as seconds " +
            "from StudySession s where s.familyId = :family and s.familyMemberId = :member " +
            "and s.status <> 'VOIDED' and s.date between :from and :to group by s.date order by s.date desc")
    List<DayCount> dayCounts(UUID family, UUID member, LocalDate from, LocalDate to);
    interface DayCount { LocalDate getDate(); Long getSessions(); Long getCompletedSessions(); Long getSeconds(); }
    @Query("select s.subjectId as subjectId, sum(s.accumulatedDurationSeconds) as seconds, count(s) as count from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status = 'COMPLETED' and s.date between :from and :to group by s.subjectId")
    List<SummaryRow> summary(UUID family, UUID member, LocalDate from, LocalDate to);
    interface SummaryRow { UUID getSubjectId(); Long getSeconds(); Long getCount(); }
}
