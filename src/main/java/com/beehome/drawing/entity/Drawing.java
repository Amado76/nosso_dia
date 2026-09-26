package com.beehome.drawing.entity;

import com.beehome.drawing.dto.DrawingDocument;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "drawings", schema = "beehome")
public class Drawing {
    @Id private UUID id;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "member_id", nullable = false) private UUID memberId;
    @Column(nullable = false, length = 40) private String surface;
    @Column(name = "format_version", nullable = false) private int formatVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private DrawingDocument document;
    @Column(nullable = false) private long revision;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Drawing() {}

    public Drawing(UUID familyId, UUID memberId, String surface, DrawingDocument document, Instant now) {
        id = UUID.randomUUID();
        this.familyId = familyId;
        this.memberId = memberId;
        this.surface = surface;
        this.createdAt = now;
        replace(document, now);
    }

    public void replace(DrawingDocument document, Instant now) {
        this.document = document;
        this.formatVersion = document.formatVersion();
        revision++;
        updatedAt = now;
    }

    public UUID getFamilyId() { return familyId; }
    public UUID getMemberId() { return memberId; }
    public String getSurface() { return surface; }
    public DrawingDocument getDocument() { return document; }
    public long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
