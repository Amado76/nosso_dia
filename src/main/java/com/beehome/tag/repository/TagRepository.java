package com.beehome.tag.repository;

import com.beehome.tag.entity.Tag;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByFamilyIdAndId(UUID familyId, UUID id);
    boolean existsByFamilyIdAndNormalizedNameAndIdNot(UUID familyId, String normalizedName, UUID id);
    @Query("select t from Tag t where t.familyId = :family and (:query is null or locate(:query, t.normalizedName) > 0) order by t.normalizedName, t.id")
    Slice<Tag> list(UUID family, String query, Pageable page);
    List<Tag> findByFamilyIdAndIdIn(UUID familyId, Collection<UUID> ids);
}
