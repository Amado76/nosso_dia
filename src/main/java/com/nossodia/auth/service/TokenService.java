package com.nossodia.auth.service;

import com.nossodia.auth.entity.RefreshToken;
import com.nossodia.auth.exception.AuthException;
import com.nossodia.auth.repository.RefreshTokenRepository;
import com.nossodia.auth.security.AuthProperties;
import com.nossodia.auth.security.TokenSecrets;

import com.nossodia.auth.dto.AuthResponse;
import com.nossodia.user.UserService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private final RefreshTokenRepository tokens;
    private final UserService users;
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    public TokenService(RefreshTokenRepository tokens, UserService users, JwtEncoder encoder,
                        AuthProperties properties, Clock clock) {
        this.tokens = tokens; this.users = users; this.encoder = encoder;
        this.properties = properties; this.clock = clock;
    }
    // Called after credential verification with the user lock held.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public AuthResponse issue(UUID userId) {
        String raw = TokenSecrets.generate();
        tokens.save(new RefreshToken(userId, raw, clock.instant(), clock.instant().plus(properties.refreshTtl())));
        return response(userId, raw);
    }
    @Transactional
    public AuthResponse refresh(String raw) {
        String hash = TokenSecrets.hash(raw);
        UUID userId = tokens.owner(hash).orElseThrow(TokenService::invalid);
        users.lock(userId);
        var previous = tokens.findByTokenHash(hash).orElseThrow(TokenService::invalid);
        var now = clock.instant();
        if (!previous.active(now)) throw invalid();
        String nextRaw = TokenSecrets.generate();
        var next = tokens.saveAndFlush(new RefreshToken(userId, nextRaw, now, now.plus(properties.refreshTtl())));
        previous.replaceWith(next.getId(), now);
        return response(userId, nextRaw);
    }
    @Transactional
    public void logout(UUID userId, String raw) {
        users.lock(userId);
        var token = tokens.findByTokenHash(TokenSecrets.hash(raw)).orElseThrow(TokenService::invalid);
        if (!token.getUserId().equals(userId)) throw invalid();
        token.revoke(clock.instant());
    }
    private AuthResponse response(UUID userId, String raw) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(userId.toString())
                .audience(java.util.List.of("nosso-dia-api")).issuedAt(now)
                .expiresAt(now.plus(properties.accessTtl())).id(UUID.randomUUID().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(access, raw, "Bearer", properties.accessTtl().toSeconds());
    }
    private static AuthException invalid() {
        return new AuthException("INVALID_REFRESH_TOKEN", "error.auth.invalid-refresh-token");
    }
}
