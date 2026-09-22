package com.nossodia.auth.entity;

import com.nossodia.auth.security.TokenSecrets;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens", schema = "nosso_dia")
public class PasswordResetToken {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected PasswordResetToken() {}
    public PasswordResetToken(UUID userId, String raw, Instant now, Instant expiresAt) {
        id = UUID.randomUUID();
        this.userId = userId;
        tokenHash = TokenSecrets.hash(raw);
        createdAt = now;
        this.expiresAt = expiresAt;
    }
    public Instant getUsedAt() { return usedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
