package com.nossodia.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_identities", schema = "nosso_dia",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_subject"}))
public class ExternalIdentity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ExternalProvider provider;
    @Column(name = "provider_subject", nullable = false) private String providerSubject;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected ExternalIdentity() {}
    public UUID getUserId() { return userId; }
}
