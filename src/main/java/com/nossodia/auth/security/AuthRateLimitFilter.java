package com.nossodia.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-process, bounded limiter. The deployment edge must enforce limits across replicas. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10000;
    private final Map<String, Bucket> buckets = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecurityErrorHandler errors;
    public AuthRateLimitFilter(AuthProperties properties, Clock clock, SecurityErrorHandler errors) {
        this.properties = properties; this.clock = clock; this.errors = errors;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Do not trust client-supplied forwarding headers.
        if (!allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.auth.rate-limited");
            return;
        }
        chain.doFilter(request, response);
    }
    private synchronized boolean allow(String address) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (currentMinute != minute) {
            buckets.clear();
            currentMinute = minute;
        }
        Bucket bucket = buckets.get(address);
        if (bucket == null) {
            if (buckets.size() >= MAX_BUCKETS) return false;
            bucket = new Bucket();
            buckets.put(address, bucket);
        }
        return ++bucket.count <= properties.requestsPerMinute();
    }
    private static final class Bucket {
        int count;
    }
}
