package com.beehome.activity.repository;

import com.beehome.activity.entity.ExtracurricularActivity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExtracurricularActivityRepository extends JpaRepository<ExtracurricularActivity, UUID> {
    Optional<ExtracurricularActivity> findByFamilyIdAndId(UUID family, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExtracurricularActivity a where a.familyId = :family and a.id = :id")
    Optional<ExtracurricularActivity> lock(UUID family, UUID id);
    @Query("select a from ExtracurricularActivity a where a.familyId = :family and (:includeInactive = true or a.active = true) order by a.sortOrder, a.id")
    List<ExtracurricularActivity> list(UUID family, boolean includeInactive);
    long countByFamilyId(UUID family);
    @Query("select a from ExtracurricularActivity a where a.familyId = :family and a.id in :ids")
    List<ExtracurricularActivity> findForIds(UUID family, Collection<UUID> ids);
}
