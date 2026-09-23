package com.beehome.routine.repository;
import com.beehome.routine.entity.RoutineItem;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;

public interface RoutineItemRepository extends JpaRepository<RoutineItem, UUID> {
    List<RoutineItem> findByFamilyIdAndRoutineIdOrderBySortOrderAscIdAsc(UUID familyId, UUID routineId, Pageable limit);
    Optional<RoutineItem> findByFamilyIdAndRoutineIdAndId(UUID familyId, UUID routineId, UUID id);
    long countByRoutineId(UUID routineId);
    @Query("""
        select i from RoutineItem i join Routine r on r.id = i.routineId and r.familyId = i.familyId
        where i.familyId = :familyId and i.familyMemberId = :memberId and i.active = true and r.active = true
        and (r.startDate is null or r.startDate <= :date) and (r.endDate is null or r.endDate >= :date)
        and :day member of r.daysOfWeek order by i.sortOrder, i.id
        """)
    List<RoutineItem> applicable(UUID familyId, UUID memberId, LocalDate date, DayOfWeek day, Pageable limit);
}
