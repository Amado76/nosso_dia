package com.beehome.media.repository;

import com.beehome.media.entity.PendingMediaDeletion;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingMediaDeletionRepository extends JpaRepository<PendingMediaDeletion, UUID> {
    List<PendingMediaDeletion> findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscMediaIdAsc(Instant now);
}
