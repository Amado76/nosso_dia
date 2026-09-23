package com.nossodia.family.entity;

import com.nossodia.family.exception.FamilyException;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import com.nossodia.shared.exception.InputException;
import java.util.UUID;

@Entity
@Table(name = "families", schema = "nosso_dia")
public class Family {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private String timezone;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Family() {}

    public Family(String name, String timezone, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        edit(name, timezone, now);
    }

    public void edit(String name, String timezone, Instant now) {
        String normalized = name == null ? null : name.strip();
        if (normalized == null || normalized.isBlank() || normalized.length() > 120) throw FamilyException.invalidName();
        if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) throw new InputException();
        ZoneId.of(timezone);
        if (!Objects.equals(this.name, normalized) || !Objects.equals(this.timezone, timezone)) {
            this.name = normalized;
            this.timezone = timezone;
            updatedAt = now;
        }
    }

    public String getTimezone() { return timezone; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
