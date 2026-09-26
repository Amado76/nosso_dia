package com.beehome.dailyplan.repository;

import com.beehome.dailyplan.entity.DailyPlanItemTag;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPlanItemTagRepository extends JpaRepository<DailyPlanItemTag,DailyPlanItemTag.Key> {
    List<DailyPlanItemTag> findByDailyPlanItemIdIn(Collection<UUID> ids);
    void deleteByDailyPlanItemId(UUID id);
}
