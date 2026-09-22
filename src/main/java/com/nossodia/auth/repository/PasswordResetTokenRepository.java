package com.nossodia.auth.repository;

import com.nossodia.auth.entity.PasswordResetToken;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    @Query("select t.userId from PasswordResetToken t where t.tokenHash = :hash")
    Optional<UUID> owner(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String hash);
    boolean existsByUserIdAndCreatedAtAfter(UUID userId, Instant after);
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int consumeAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
