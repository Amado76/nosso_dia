package com.beehome.study.repository;

import com.beehome.study.entity.StudySubject;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface StudySubjectRepository extends JpaRepository<StudySubject, UUID> {
    long countByFamilyId(UUID familyId);
    Optional<StudySubject> findByFamilyIdAndId(UUID familyId, UUID id);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySubject s where s.familyId = :family and s.id = :id")
    Optional<StudySubject> lockByFamilyIdAndId(UUID family, UUID id);
    @Query("select s from StudySubject s where s.familyId = :family and (:includeInactive = true or s.active = true) order by s.sortOrder, s.id")
    List<StudySubject> list(UUID family, boolean includeInactive);
}
