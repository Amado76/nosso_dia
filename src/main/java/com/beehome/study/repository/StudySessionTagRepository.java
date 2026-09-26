package com.beehome.study.repository;

import com.beehome.study.entity.StudySessionTag;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionTagRepository extends JpaRepository<StudySessionTag,StudySessionTag.Key> {
    List<StudySessionTag> findBySessionIdIn(Collection<UUID> sessionIds);
    void deleteBySessionId(UUID sessionId);
}
