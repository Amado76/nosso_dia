package com.beehome.photorecord.repository;

import com.beehome.photorecord.entity.PhotoRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PhotoRecordRepository extends JpaRepository<PhotoRecord, UUID> {
    Optional<PhotoRecord> findByFamilyIdAndChildIdAndId(UUID familyId, UUID childId, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PhotoRecord r where r.familyId=:familyId and r.childId=:childId and r.id=:id")
    Optional<PhotoRecord> lock(UUID familyId, UUID childId, UUID id);
    @Query("select r from PhotoRecord r where r.familyId=:familyId and r.childId=:childId " +
            "and r.date between :from and :to " +
            "and (:query = '' or locate(:query,lower(r.description)) > 0) " +
            "and (select count(distinct t.tagId) from PhotoRecordTag t where t.photoRecordId=r.id and t.tagId in :tagIds)=:tagCount " +
            "order by r.date desc, r.createdAt desc, r.id desc")
    Slice<PhotoRecord> history(UUID familyId, UUID childId, LocalDate from, LocalDate to,
            String query, Collection<UUID> tagIds, long tagCount, Pageable page);
    @Query("select r from PhotoRecord r where r.familyId=:familyId and r.childId=:childId and r.date between :from and :to order by r.date desc, r.createdAt desc, r.id desc")
    List<PhotoRecord> range(UUID familyId, UUID childId, LocalDate from, LocalDate to);
    @Query("select distinct r.date from PhotoRecord r where r.familyId=:familyId and r.childId=:childId and r.date between :from and :to order by r.date")
    List<LocalDate> dates(UUID familyId, UUID childId, LocalDate from, LocalDate to);
    @Query("select r.date as date, count(distinct r.id) as records, count(l.id) as images " +
            "from PhotoRecord r left join PhotoRecordMedia l on l.photoRecordId = r.id " +
            "where r.familyId = :familyId and r.childId = :childId and r.date between :from and :to " +
            "group by r.date order by r.date desc")
    List<DayCount> dayCounts(UUID familyId, UUID childId, LocalDate from, LocalDate to);
    interface DayCount { LocalDate getDate(); Long getRecords(); Long getImages(); }
}
