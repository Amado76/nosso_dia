package com.beehome.auth.security;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

class TokenConfigurationTests {
    private final AuthConfiguration configuration = new AuthConfiguration();
    private AuthProperties properties(String key, boolean development) {
        return new AuthProperties(key, development, "beehome", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 30);
    }

    @Test
    void requiresAConfiguredStrongKeyOutsideDevelopment() {
        assertThatThrownBy(() -> configuration.signingKey(properties(null, false))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.signingKey(properties("invalid!", false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuration.signingKey(properties(Base64.getEncoder().encodeToString(new byte[8]), false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceSubjectExpirationAndSignature() throws Exception {
        var properties = properties(null, true);
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var key = configuration.signingKey(properties);
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key, properties, Clock.fixed(now, ZoneOffset.UTC));
        for (int scenario = 0; scenario < 5; scenario++) {
            var claims = JwtClaimsSet.builder().issuer(scenario == 0 ? "untrusted" : "beehome")
                    .subject(scenario == 1 ? "not-a-uuid" : UUID.randomUUID().toString())
                    .audience(List.of(scenario == 2 ? "another-api" : "beehome-api"))
                    .issuedAt(now.minusSeconds(120));
            if (scenario != 3) claims.expiresAt(scenario == 4 ? now.minusSeconds(1) : now.plusSeconds(60));
            String raw = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
            assertThatThrownBy(() -> decoder.decode(raw)).isInstanceOf(JwtException.class);
        }
        var validClaims = JwtClaimsSet.builder().issuer("beehome").subject(UUID.randomUUID().toString())
                .audience(List.of("beehome-api")).issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        var params = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), validClaims);
        assertThat(decoder.decode(encoder.encode(params).getTokenValue()).getSubject()).isEqualTo(validClaims.getSubject());
        var foreignEncoder = configuration.jwtEncoder(configuration.signingKey(properties));
        String forged = foreignEncoder.encode(params).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
