package com.beehome.activity.entity;

import com.beehome.shared.exception.InputException;
import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "extracurricular_activities", schema = "beehome")
public class ExtracurricularActivity {
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 120) private String normalizedName;
    @Column(length = 2000) private String description;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected ExtracurricularActivity() {}
    public ExtracurricularActivity(UUID familyId, String name, String description, int sortOrder, Instant now) {
        id = UUID.randomUUID(); this.familyId = familyId; active = true; createdAt = now;
        edit(name, description, sortOrder, now);
    }
    public void edit(String name, String description, int sortOrder, Instant now) {
        String value = name == null ? null : name.strip();
        if (value == null || value.isBlank() || value.length() > 120 || sortOrder < 0) throw new InputException();
        this.name = value;
        normalizedName = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        if (normalizedName.length() > 120) throw new InputException();
        this.description = clean(description, 2000);
        this.sortOrder = sortOrder; updatedAt = now;
    }
    public static String clean(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String result = value.strip();
        if (result.length() > maximum) throw new InputException();
        return result;
    }
    public void active(boolean value, Instant now) { active = value; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
