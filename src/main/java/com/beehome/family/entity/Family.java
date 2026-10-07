package com.beehome.family.entity;

import com.beehome.family.exception.FamilyException;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "families", schema = "beehome")
public class Family {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Family() {}

    public Family(String name, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        edit(name, now);
    }

    public void edit(String name, Instant now) {
        String normalized = name == null ? null : name.strip();
        if (normalized == null || normalized.isBlank() || normalized.length() > 120) throw FamilyException.invalidName();
        if (!Objects.equals(this.name, normalized)) {
            this.name = normalized;
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
