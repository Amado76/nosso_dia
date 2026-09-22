package com.nossodia.auth.service;

import com.nossodia.auth.entity.PasswordResetToken;
import com.nossodia.auth.exception.AuthException;
import com.nossodia.auth.mail.PasswordResetDelivery;
import com.nossodia.auth.repository.PasswordResetTokenRepository;
import com.nossodia.auth.repository.RefreshTokenRepository;
import com.nossodia.auth.security.AuthProperties;
import com.nossodia.auth.security.TokenSecrets;

import com.nossodia.user.service.UserService;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {
    private final UserService users;
    private final PasswordResetTokenRepository resets;
    private final RefreshTokenRepository sessions;
    private final PasswordEncoder passwords;
    private final PasswordResetDelivery delivery;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PasswordResetService(UserService users, PasswordResetTokenRepository resets,
            RefreshTokenRepository sessions, PasswordEncoder passwords, PasswordResetDelivery delivery,
            AuthProperties properties, Clock clock, org.springframework.transaction.PlatformTransactionManager manager) {
        this.users = users; this.resets = resets; this.sessions = sessions; this.passwords = passwords;
        this.delivery = delivery; this.properties = properties; this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }
    public void forgot(String email) {
        String raw = TokenSecrets.generate();
        String recipient = transactions.execute(status -> {
            var found = users.findForAuthentication(email);
            if (found.isEmpty()) return null;
            var user = users.lock(found.get().getId());
            var now = clock.instant();
            if (resets.existsByUserIdAndCreatedAtAfter(user.getId(), now.minus(properties.resetCooldown()))) return null;
            resets.save(new PasswordResetToken(user.getId(), raw, now, now.plus(properties.resetTtl())));
            return user.getEmail();
        });
        if (recipient != null) {
            try {
                delivery.send(recipient, raw);
            } catch (RuntimeException ignored) {
                // Delivery failures must not turn account existence into an API error oracle.
            }
        }
    }
    public void reset(String raw, String password) {
        // Expensive hashing runs outside the database lock.
        String passwordHash = passwords.encode(password);
        transactions.executeWithoutResult(status -> {
            String hash = TokenSecrets.hash(raw);
            var userId = resets.owner(hash).orElseThrow(PasswordResetService::invalid);
            var user = users.lock(userId);
            var token = resets.findByTokenHash(hash).orElseThrow(PasswordResetService::invalid);
            var now = clock.instant();
            if (token.getUsedAt() != null) throw invalid();
            if (!token.getExpiresAt().isAfter(now))
                throw new AuthException(HttpStatus.BAD_REQUEST, "EXPIRED_RESET_TOKEN", "error.auth.expired-reset-token");
            users.changePassword(user, passwordHash);
            resets.consumeAll(userId, now);
            sessions.revokeAll(userId, now);
        });
    }
    public void change(java.util.UUID userId, String currentPassword, String newPassword) {
        String previousHash = users.passwordHash(userId);
        if (previousHash == null || !passwords.matches(currentPassword, previousHash)) throw invalidCurrentPassword();
        String nextHash = passwords.encode(newPassword);
        transactions.executeWithoutResult(status -> {
            var user = users.lock(userId);
            // Reject a concurrent credential change after verification outside the lock.
            if (!previousHash.equals(user.getPasswordHash())) throw invalidCurrentPassword();
            users.changePassword(user, nextHash);
            var now = clock.instant();
            resets.consumeAll(userId, now);
            sessions.revokeAll(userId, now);
        });
    }
    private static AuthException invalidCurrentPassword() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", "error.auth.invalid-current-password");
    }
    private static AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN", "error.auth.invalid-reset-token");
    }
}
