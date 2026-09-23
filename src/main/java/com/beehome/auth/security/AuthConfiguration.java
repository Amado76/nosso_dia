package com.beehome.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id,
                Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    SecretKey signingKey(AuthProperties properties) throws NoSuchAlgorithmException {
        if (properties.signingKey() != null && !properties.signingKey().isBlank()) {
            byte[] key;
            try {
                key = Base64.getDecoder().decode(properties.signingKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("AUTH_SIGNING_KEY must be Base64");
            }
            if (key.length < 32) {
                throw new IllegalArgumentException("AUTH_SIGNING_KEY must contain at least 32 random bytes");
            }
            return new SecretKeySpec(key, "HmacSHA256");
        }
        if (!properties.allowEphemeralKey()) {
            throw new IllegalStateException("AUTH_SIGNING_KEY is required outside local development");
        }
        var generator = KeyGenerator.getInstance("HmacSHA256");
        generator.init(256);
        return generator.generateKey();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, AuthProperties properties, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        OAuth2TokenValidator<Jwt> identity = jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                if (jwt.getExpiresAt() != null && jwt.getAudience().contains("beehome-api")) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (RuntimeException ignored) {
                // A missing or malformed subject must remain an authentication failure.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp, new JwtIssuerValidator(properties.issuer()), identity));
        return decoder;
    }
}
