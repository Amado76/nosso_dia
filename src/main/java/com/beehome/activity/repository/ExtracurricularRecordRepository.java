package com.beehome.activity.repository;

import com.beehome.activity.entity.ExtracurricularRecord;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ExtracurricularRecordRepository extends JpaRepository<ExtracurricularRecord, UUID> {
    Optional<ExtracurricularRecord> findByFamilyIdAndChildIdAndId(UUID family, UUID child, UUID id);
    @Query("select r from ExtracurricularRecord r where r.familyId=:family and r.childId=:child and r.date between :from and :to order by r.date desc, r.id desc")
    List<ExtracurricularRecord> range(UUID family, UUID child, LocalDate from, LocalDate to);
    @Query("select r from ExtracurricularRecord r where r.familyId=:family and r.childId=:child and r.date between :from and :to order by r.date desc, r.id desc")
    Slice<ExtracurricularRecord> history(UUID family, UUID child, LocalDate from, LocalDate to, Pageable page);
    @Query("select distinct r.date from ExtracurricularRecord r where r.familyId=:family and r.childId=:child and r.date between :from and :to order by r.date")
    List<LocalDate> dates(UUID family, UUID child, LocalDate from, LocalDate to);
    @Query("select r.date as date, count(r) as records, coalesce(sum(r.durationMinutes),0) as minutes from ExtracurricularRecord r where r.familyId=:family and r.childId=:child and r.date between :from and :to group by r.date")
    List<DayCount> dayCounts(UUID family, UUID child, LocalDate from, LocalDate to);
    interface DayCount { LocalDate getDate(); Long getRecords(); Long getMinutes(); }
    @Query("select r.activityId as activityId, count(r) as records, coalesce(sum(r.durationMinutes),0) as minutes from ExtracurricularRecord r where r.familyId=:family and r.childId=:child and r.date between :from and :to group by r.activityId")
    List<SummaryRow> summary(UUID family, UUID child, LocalDate from, LocalDate to);
    interface SummaryRow { UUID getActivityId(); Long getRecords(); Long getMinutes(); }
}
