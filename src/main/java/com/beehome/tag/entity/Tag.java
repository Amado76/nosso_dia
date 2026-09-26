package com.beehome.tag.entity;

import com.beehome.shared.exception.InputException;
import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "tags", schema = "beehome")
public class Tag {
    @Id private UUID id;
    private UUID familyId;
    @Column(length = 120) private String name;
    @Column(length = 120) private String normalizedName;
    @Column(length = 7) private String color;
    private Instant createdAt;
    private Instant updatedAt;

    protected Tag() {}

    public Tag(UUID familyId, String name, String color, Instant now) {
        this.id = UUID.randomUUID();
        this.familyId = familyId;
        this.createdAt = now;
        edit(name, color, now);
    }

    public void edit(String name, String color, Instant now) {
        if (name == null) throw new InputException();
        String display = Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
        if (display.isBlank() || display.length() > 120) throw new InputException();
        if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) throw new InputException();
        this.name = display;
        String normalized = Normalizer.normalize(display.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        if (normalized.length() > 120) throw new InputException();
        this.normalizedName = normalized;
        this.color = color;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
