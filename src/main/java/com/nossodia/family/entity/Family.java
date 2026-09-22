package com.nossodia.family.entity;

import com.nossodia.family.exception.FamilyException;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "families", schema = "nosso_dia")
public class Family {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Family() {}

    public Family(String name, Instant now) {
        id = UUID.randomUUID();
        createdAt = now;
        rename(name, now);
    }

    public void rename(String name, Instant now) {
        String normalized = name == null ? null : name.strip();
        if (normalized == null || normalized.isBlank() || normalized.length() > 120) {
            throw FamilyException.invalidName();
        }
        this.name = normalized;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
