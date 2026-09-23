package com.beehome.familymember.entity;

import com.beehome.familymember.exception.FamilyMemberException;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "family_members", schema = "beehome")
public class FamilyMember {
    @Id private UUID id;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(nullable = false, length = 120) private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 10) private MemberType memberType;
    @Column(name = "birth_date") private LocalDate birthDate;
    @Column(length = 7) private String color;
    @Column(name = "avatar_reference", length = 255) private String avatarReference;
    @Column(name = "linked_user_id") private UUID linkedUserId;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected FamilyMember() {}

    public FamilyMember(UUID familyId, String name, MemberType memberType, LocalDate birthDate,
            String color, LocalDate today, Instant now) {
        this.id = UUID.randomUUID();
        this.familyId = Objects.requireNonNull(familyId);
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
        edit(name, memberType, birthDate, color, today, now);
    }

    public void edit(String name, MemberType memberType, LocalDate birthDate, String color, LocalDate today, Instant now) {
        String normalized = name == null ? null : name.strip();
        if (normalized == null || normalized.isBlank() || normalized.length() > 120) throw FamilyMemberException.invalid("name");
        if (memberType == null) throw FamilyMemberException.invalid("member-type");
        if (birthDate != null && birthDate.isAfter(today)) throw FamilyMemberException.invalid("birth-date");
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) throw FamilyMemberException.invalid("color");
        String canonicalColor = color == null ? null : color.toUpperCase(Locale.ROOT);
        if (!Objects.equals(this.name, normalized) || this.memberType != memberType
                || !Objects.equals(this.birthDate, birthDate) || !Objects.equals(this.color, canonicalColor)) {
            this.name = normalized;
            this.memberType = memberType;
            this.birthDate = birthDate;
            this.color = canonicalColor;
            this.updatedAt = now;
        }
    }

    public void setActive(boolean active, Instant now) {
        if (this.active != active) {
            this.active = active;
            this.updatedAt = now;
        }
    }

    public void link(UUID userId, Instant now) {
        Objects.requireNonNull(userId);
        if (userId.equals(linkedUserId)) return;
        if (linkedUserId != null) throw FamilyMemberException.alreadyLinked();
        if (!active) throw FamilyMemberException.inactive();
        linkedUserId = userId;
        updatedAt = now;
    }

    public void unlink(Instant now) {
        if (linkedUserId != null) {
            linkedUserId = null;
            updatedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public MemberType getMemberType() { return memberType; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getColor() { return color; }
    public String getAvatarReference() { return avatarReference; }
    public UUID getLinkedUserId() { return linkedUserId; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
