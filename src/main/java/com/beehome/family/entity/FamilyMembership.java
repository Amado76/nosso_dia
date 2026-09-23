package com.beehome.family.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family_memberships", schema = "beehome")
public class FamilyMembership {
    @Id private UUID id;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private FamilyRole role;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected FamilyMembership() {}

    public FamilyMembership(UUID familyId, UUID userId, FamilyRole role, Instant now) {
        id = UUID.randomUUID();
        this.familyId = familyId;
        this.userId = userId;
        this.role = role;
        createdAt = now;
    }

    public FamilyRole getRole() { return role; }
}
