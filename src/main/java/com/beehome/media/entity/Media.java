package com.beehome.media.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media", schema = "beehome")
public class Media {
    @Id private UUID id;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "uploaded_by_user_id", nullable = false) private UUID uploadedByUserId;
    @Column(nullable = false, length = 10) private String type;
    @Column(name = "storage_key", nullable = false, length = 120) private String storageKey;
    @Column(name = "original_filename", length = 255) private String originalFilename;
    @Column(name = "mime_type", nullable = false, length = 20) private String mimeType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected Media() {}
    public Media(UUID id, UUID familyId, UUID userId, String key, String filename, String mime, long size, Instant now) {
        this.id=id; this.familyId=familyId; this.uploadedByUserId=userId; this.type="IMAGE";
        this.storageKey=key; this.originalFilename=filename; this.mimeType=mime; this.sizeBytes=size; this.createdAt=now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getType() { return type; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
