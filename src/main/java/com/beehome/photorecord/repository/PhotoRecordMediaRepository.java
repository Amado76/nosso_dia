package com.beehome.photorecord.repository;

import com.beehome.photorecord.entity.PhotoRecordMedia;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PhotoRecordMediaRepository extends JpaRepository<PhotoRecordMedia, UUID> {
    List<PhotoRecordMedia> findByPhotoRecordIdOrderByPosition(UUID id);
    @Query("select l from PhotoRecordMedia l where l.photoRecordId in :ids order by l.photoRecordId, l.position")
    List<PhotoRecordMedia> findForRecords(Collection<UUID> ids);
    boolean existsByMediaId(UUID mediaId);
    @Modifying
    @Query("delete from PhotoRecordMedia l where l.photoRecordId = :id")
    void deleteForRecord(UUID id);
}
