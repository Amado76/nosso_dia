package com.beehome.auth.entity;

import com.beehome.auth.security.TokenSecrets;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", schema = "beehome")
public class RefreshToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "replaced_by") private UUID replacedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected RefreshToken() {}
    public RefreshToken(UUID userId, String raw, Instant now, Instant expiresAt) {
        id = UUID.randomUUID();
        this.userId = userId;
        tokenHash = TokenSecrets.hash(raw);
        createdAt = now;
        this.expiresAt = expiresAt;
    }
    public boolean active(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public void replaceWith(UUID replacementId, Instant now) {
        revokedAt = now;
        replacedBy = replacementId;
    }
    public void revoke(Instant now) {
        if (revokedAt == null) revokedAt = now;
    }
}
