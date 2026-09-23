package com.beehome.auth.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth")
public record AuthProperties(String signingKey, boolean allowEphemeralKey, String issuer,
        Duration accessTtl, Duration refreshTtl, Duration resetTtl, Duration resetCooldown,
        boolean publicDocs, int requestsPerMinute) {
    public AuthProperties {
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("auth.issuer is required");
        for (Duration duration : new Duration[]{accessTtl, refreshTtl, resetTtl, resetCooldown}) {
            if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0)
                throw new IllegalArgumentException("Authentication durations must be at least one second");
        }
        if (accessTtl.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Access token lifetime must not exceed one hour");
        if (requestsPerMinute < 1) throw new IllegalArgumentException("Rate limit must be positive");
    }
    @Override public String toString() { return "AuthProperties[redacted]"; }
}
