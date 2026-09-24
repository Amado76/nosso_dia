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
            "order by r.date desc, r.createdAt desc, r.id desc")
    Slice<PhotoRecord> history(UUID familyId, UUID childId, LocalDate from, LocalDate to, Pageable page);
}
