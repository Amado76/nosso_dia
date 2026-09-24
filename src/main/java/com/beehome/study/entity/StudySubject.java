package com.beehome.study.entity;

import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import com.beehome.shared.exception.InputException;

@Entity
@Table(name = "study_subjects", schema = "beehome")
public class StudySubject {
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 120) private String normalizedName;
    @Column(length = 7) private String color;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected StudySubject() {}
    public StudySubject(UUID familyId, String name, String color, int sortOrder, Instant now) {
        this.id = UUID.randomUUID(); this.familyId = familyId; this.active = true; this.createdAt = now;
        edit(name, color, sortOrder, now);
    }
    public void edit(String name, String color, int sortOrder, Instant now) {
        String trimmed = name == null ? null : name.strip();
        if (trimmed == null || trimmed.isBlank() || trimmed.length() > 120 || sortOrder < 0 ||
                (color != null && !color.matches("#[0-9A-Fa-f]{6}"))) throw new InputException();
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) throw new InputException();
        this.name = trimmed;
        this.normalizedName = normalized;
        this.color = color; this.sortOrder = sortOrder; this.updatedAt = now;
    }
    public void active(boolean value, Instant now) { active = value; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
