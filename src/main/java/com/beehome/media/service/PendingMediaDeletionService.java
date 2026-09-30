package com.beehome.media.service;

import com.beehome.media.repository.PendingMediaDeletionRepository;
import com.beehome.media.storage.MediaStorage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PendingMediaDeletionService {
    private static final Logger log = LoggerFactory.getLogger(PendingMediaDeletionService.class);
    private final PendingMediaDeletionRepository pending;
    private final MediaStorage storage;
    private final Clock clock;

    public PendingMediaDeletionService(PendingMediaDeletionRepository pending, MediaStorage storage, Clock clock) {
        this.pending = pending;
        this.storage = storage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${media.delete-retry-interval-ms:60000}",
            initialDelayString = "${media.delete-retry-interval-ms:60000}")
    public void cleanupPendingDeletes() {
        for (var deletion : pending.findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscMediaIdAsc(clock.instant())) {
            try {
                storage.delete(deletion.getStorageKey());
                pending.deleteById(deletion.getMediaId());
            } catch (IOException e) {
                log.warn("Pending media deletion failed for {}", deletion.getMediaId(), e);
                deletion.retryAt(clock.instant().plus(Duration.ofMinutes(5)));
                pending.save(deletion);
            } catch (RuntimeException e) {
                log.warn("Pending media deletion acknowledgement failed for {}", deletion.getMediaId(), e);
            }
        }
    }
}
