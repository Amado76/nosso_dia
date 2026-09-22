package com.nossodia.auth.security;

import jakarta.servlet.FilterChain;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;

class AuthRateLimitTests {
    @Test
    void limitsAuthenticationRequestsWithoutTrustingForwardedAddresses() throws Exception {
        var properties = new AuthProperties(null, true, "nosso-dia", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 2);
        var errors = mock(SecurityErrorHandler.class);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        var filter = new AuthRateLimitFilter(properties, clock, errors);
        var chain = mock(FilterChain.class);
        for (int attempt = 0; attempt < 3; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "192.0.2." + (attempt + 10));
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(2)).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.auth.rate-limited"));
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:01:00Z"));
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.0.2.1");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain, times(3)).doFilter(any(), any());
    }

    @Test
    void doesNotThrottleHealthOrAuthenticatedBusinessReads() throws Exception {
        var properties = new AuthProperties(null, true, "nosso-dia", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 1);
        var errors = mock(SecurityErrorHandler.class);
        var filter = new AuthRateLimitFilter(properties, Clock.systemUTC(), errors);
        var chain = mock(FilterChain.class);
        for (String path : new String[]{"/api/health", "/api/users/me", "/api/users/me"})
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
        verify(chain, times(3)).doFilter(any(), any());
        verifyNoInteractions(errors);
    }
}
