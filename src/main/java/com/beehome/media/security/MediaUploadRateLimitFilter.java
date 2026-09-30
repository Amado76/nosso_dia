package com.beehome.media.security;

import com.beehome.auth.security.SecurityErrorHandler;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-process ingress limit; deployments with multiple replicas also need an edge limit. */
public class MediaUploadRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(MediaUploadRateLimitFilter.class);
    private static final int MAX_BUCKETS = 10000;
    private final int requestsPerMinute;
    private final Clock clock;
    private final SecurityErrorHandler errors;
    private final Semaphore uploadSlots;
    private final Map<String, Integer> buckets = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;

    public MediaUploadRateLimitFilter(int requestsPerMinute, Clock clock, SecurityErrorHandler errors) {
        this(requestsPerMinute, 2, clock, errors);
    }

    public MediaUploadRateLimitFilter(int requestsPerMinute, int maxConcurrent, Clock clock, SecurityErrorHandler errors) {
        if (requestsPerMinute <= 0) throw new IllegalArgumentException("media.upload-requests-per-minute must be positive");
        if (maxConcurrent <= 0) throw new IllegalArgumentException("media.max-concurrent-uploads must be positive");
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
        this.errors = errors;
        this.uploadSlots = new Semaphore(maxConcurrent);
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
        if (!uploadSlots.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.media.rate-limited");
            return;
        }
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            uploadSlots.release();
            log.debug("Media upload request finished: durationMs={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
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
