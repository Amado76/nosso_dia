package com.beehome.routine.repository;
import com.beehome.routine.entity.Routine;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {
    Optional<Routine> findByFamilyIdAndId(UUID familyId, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Routine r where r.familyId = :familyId and r.id = :id")
    Optional<Routine> lock(UUID familyId, UUID id);
    @Query("select r from Routine r where r.familyId = :familyId and (:includeInactive = true or r.active = true) order by r.createdAt, r.id")
    Slice<Routine> list(UUID familyId, boolean includeInactive, Pageable page);
}
