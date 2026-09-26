package com.beehome.media.security;

import com.beehome.auth.security.SecurityErrorHandler;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-process ingress limit; deployments with multiple replicas also need an edge limit. */
public class MediaUploadRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10000;
    private final int requestsPerMinute;
    private final Clock clock;
    private final SecurityErrorHandler errors;
    private final Map<String, Integer> buckets = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;

    public MediaUploadRateLimitFilter(int requestsPerMinute, Clock clock, SecurityErrorHandler errors) {
        if (requestsPerMinute <= 0) throw new IllegalArgumentException("media.upload-requests-per-minute must be positive");
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
        this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) ||
                !path.matches("/api/families/[0-9a-fA-F-]{36}/media");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Forwarding headers are untrusted; the edge must supply its own shared limiter.
        if (!allow(request.getRemoteAddr() + ":" + request.getRequestURI())) {
            response.setHeader("Retry-After", "60");
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.media.rate-limited");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String key) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (currentMinute != minute) { buckets.clear(); currentMinute = minute; }
        int count = buckets.getOrDefault(key, 0);
        if (count == 0 && buckets.size() >= MAX_BUCKETS) return false;
        buckets.put(key, count + 1);
        return count < requestsPerMinute;
    }
}
