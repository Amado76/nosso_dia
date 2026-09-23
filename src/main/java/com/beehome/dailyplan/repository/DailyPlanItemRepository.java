package com.beehome.dailyplan.repository;
import com.beehome.dailyplan.entity.DailyPlanItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface DailyPlanItemRepository extends JpaRepository<DailyPlanItem, UUID> {
    List<DailyPlanItem> findByDailyPlanIdOrderBySortOrderAscIdAsc(UUID planId, Pageable limit);
    Optional<DailyPlanItem> findByDailyPlanIdAndId(UUID planId, UUID id);
    long countByDailyPlanId(UUID planId);
    boolean existsByDailyPlanIdAndSortOrderAndIdNot(UUID planId, int sortOrder, UUID id);
}
