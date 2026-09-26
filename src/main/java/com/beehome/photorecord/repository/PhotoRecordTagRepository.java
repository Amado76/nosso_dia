package com.beehome.photorecord.repository;

import com.beehome.photorecord.entity.PhotoRecordTag;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PhotoRecordTagRepository extends JpaRepository<PhotoRecordTag, PhotoRecordTag.Key> {
    List<PhotoRecordTag> findByPhotoRecordIdIn(Collection<UUID> ids);
    @Modifying
    @Query("delete from PhotoRecordTag t where t.photoRecordId=:id")
    void deleteForRecord(UUID id);
}
