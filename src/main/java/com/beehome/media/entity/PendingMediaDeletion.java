package com.beehome.media.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_media_deletions", schema = "beehome")
public class PendingMediaDeletion {
    @Id @Column(name = "media_id") private UUID mediaId;
    @Column(name = "storage_key", nullable = false, length = 120) private String storageKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;

    protected PendingMediaDeletion() {}
    public PendingMediaDeletion(UUID mediaId, String storageKey, Instant createdAt) {
        this.mediaId = mediaId;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }
    public UUID getMediaId() { return mediaId; }
    public String getStorageKey() { return storageKey; }
    public void retryAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
}
