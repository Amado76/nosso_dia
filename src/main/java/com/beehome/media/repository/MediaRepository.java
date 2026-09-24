package com.beehome.media.repository;

import com.beehome.media.entity.Media;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    Optional<Media> findByFamilyIdAndId(UUID familyId, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Media m where m.familyId = :familyId and m.id = :id")
    Optional<Media> lock(UUID familyId, UUID id);
    @Query("select m from Media m where m.familyId = :familyId and (:unattached = false or not exists " +
            "(select l.id from PhotoRecordMedia l where l.mediaId = m.id)) order by m.createdAt desc, m.id desc")
    Slice<Media> list(UUID familyId, boolean unattached, Pageable page);
}
