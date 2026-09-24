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
    @Query("select s.subjectId as subjectId, sum(s.accumulatedDurationSeconds) as seconds, count(s) as count from StudySession s where s.familyId = :family and s.familyMemberId = :member and s.status = 'COMPLETED' and s.date between :from and :to group by s.subjectId")
    List<SummaryRow> summary(UUID family, UUID member, LocalDate from, LocalDate to);
    interface SummaryRow { UUID getSubjectId(); Long getSeconds(); Long getCount(); }
}
