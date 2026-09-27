package com.beehome.auth.security;

import jakarta.servlet.FilterChain;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;

class AuthRateLimitTests {
    @Test
    void limitsAuthenticationRequestsWithoutTrustingForwardedAddresses() throws Exception {
        var properties = new AuthProperties(null, true, "beehome", Duration.ofMinutes(15),
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
        var properties = new AuthProperties(null, true, "beehome", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 1);
        var errors = mock(SecurityErrorHandler.class);
        var filter = new AuthRateLimitFilter(properties, Clock.systemUTC(), errors);
        var chain = mock(FilterChain.class);
        for (String path : new String[]{"/api/health", "/api/users/me", "/api/users/me"})
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);
        verify(chain, times(3)).doFilter(any(), any());
        verifyNoInteractions(errors);
    }

    @Test
    void usesClientAddressOnlyFromConfiguredProxy() throws Exception {
        var properties = new AuthProperties(null, true, "beehome", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 2);
        var errors = mock(SecurityErrorHandler.class);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        var filter = new AuthRateLimitFilter(properties, clock, errors, "172.30.40.254");
        var chain = mock(FilterChain.class);

        for (String client : new String[]{"192.0.2.1", "198.51.100.2", "192.0.2.1", "198.51.100.2", "192.0.2.1"}) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("172.30.40.254");
            request.addHeader("X-BeeHome-Client-IP", client);
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        verify(chain, times(4)).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.auth.rate-limited"));
    }

    @Test
    void ignoresClientAddressHeaderFromOtherPeers() throws Exception {
        var properties = new AuthProperties(null, true, "beehome", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 1);
        var errors = mock(SecurityErrorHandler.class);
        var filter = new AuthRateLimitFilter(properties, Clock.systemUTC(), errors, "172.30.40.254");
        var chain = mock(FilterChain.class);

        for (String client : new String[]{"192.0.2.1", "198.51.100.2"}) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("203.0.113.9");
            request.addHeader("X-BeeHome-Client-IP", client);
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        verify(chain).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.auth.rate-limited"));
    }

    @Test
    void rejectsMalformedOrRepeatedClientAddressHeadersFromProxy() throws Exception {
        var properties = new AuthProperties(null, true, "beehome", Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofMinutes(30), Duration.ofMinutes(5), false, 1);
        var errors = mock(SecurityErrorHandler.class);
        var filter = new AuthRateLimitFilter(properties, Clock.systemUTC(), errors, "172.30.40.254");
        var chain = mock(FilterChain.class);

        var malformed = new MockHttpServletRequest("POST", "/api/auth/login");
        malformed.setRemoteAddr("172.30.40.254");
        malformed.addHeader("X-BeeHome-Client-IP", "192.0.2.1, 198.51.100.2");
        filter.doFilter(malformed, new MockHttpServletResponse(), chain);

        var repeated = new MockHttpServletRequest("POST", "/api/auth/login");
        repeated.setRemoteAddr("172.30.40.254");
        repeated.addHeader("X-BeeHome-Client-IP", "192.0.2.1");
        repeated.addHeader("X-BeeHome-Client-IP", "198.51.100.2");
        filter.doFilter(repeated, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.auth.rate-limited"));
    }
}
