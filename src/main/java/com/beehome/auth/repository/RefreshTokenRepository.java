package com.beehome.auth.repository;

import com.beehome.auth.entity.RefreshToken;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @Query("select t.userId from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> owner(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    boolean existsByUserIdAndSessionIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, UUID sessionId, Instant now);
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.sessionId = :sessionId and t.revokedAt is null")
    int revokeSession(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId, @Param("now") Instant now);
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
