package com.beehome.activity.repository;

import com.beehome.activity.entity.ExtracurricularRecordTag;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExtracurricularRecordTagRepository extends JpaRepository<ExtracurricularRecordTag, ExtracurricularRecordTag.Key> {
    List<ExtracurricularRecordTag> findByRecordIdIn(Collection<UUID> ids);
    @Modifying @Query("delete from ExtracurricularRecordTag t where t.recordId = :id")
    void deleteForRecord(UUID id);
}
