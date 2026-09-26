package com.beehome.routine.repository;

import com.beehome.routine.entity.RoutineItemTag;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineItemTagRepository extends JpaRepository<RoutineItemTag,RoutineItemTag.Key> {
    List<RoutineItemTag> findByRoutineItemIdIn(Collection<UUID> ids);
    void deleteByRoutineItemId(UUID id);
}
