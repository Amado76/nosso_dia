package com.beehome.photorecord.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "photo_records", schema = "beehome")
public class PhotoRecord {
    @Id private UUID id;
    @Column(name="family_id", nullable=false) private UUID familyId;
    @Column(name="child_id", nullable=false) private UUID childId;
    @Column(name="record_date", nullable=false) private LocalDate date;
    @Column(length=2000) private String description;
    @Column(name="created_by_user_id", nullable=false) private UUID createdByUserId;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected PhotoRecord() {}
    public PhotoRecord(UUID familyId, UUID childId, UUID userId, LocalDate date, String description, Instant now) {
        id=UUID.randomUUID(); this.familyId=familyId; this.childId=childId; this.createdByUserId=userId;
        createdAt=now; replace(date, description, now);
    }
    public void replace(LocalDate date, String description, Instant now) {
        this.date=date; this.description=description; this.updatedAt=now;
    }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getChildId() { return childId; }
    public LocalDate getDate() { return date; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
