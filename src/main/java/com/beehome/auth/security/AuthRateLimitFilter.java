package com.beehome.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-process, bounded limiter. The deployment edge must enforce limits across replicas. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10000;
    private static final String CLIENT_ADDRESS_HEADER = "X-BeeHome-Client-IP";
    private final Map<String, Bucket> buckets = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecurityErrorHandler errors;
    private final String trustedProxyAddress;
    public AuthRateLimitFilter(AuthProperties properties, Clock clock, SecurityErrorHandler errors) {
        this(properties, clock, errors, "");
    }
    public AuthRateLimitFilter(AuthProperties properties, Clock clock, SecurityErrorHandler errors,
            String trustedProxyAddress) {
        this.properties = properties; this.clock = clock; this.errors = errors;
        this.trustedProxyAddress = trustedProxyAddress == null || trustedProxyAddress.isBlank()
                ? "" : InetAddress.ofLiteral(trustedProxyAddress).getHostAddress();
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith(request.getContextPath() + "/api/auth/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(clientAddress(request))) {
            response.setHeader("Retry-After", "60");
            errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "error.auth.rate-limited");
            return;
        }
        chain.doFilter(request, response);
    }
    private String clientAddress(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!peer.equals(trustedProxyAddress)) return peer;
        // The ingress overwrites this header; never accept it from another peer.
        var values = Collections.list(request.getHeaders(CLIENT_ADDRESS_HEADER));
        if (values.size() != 1) return peer;
        try {
            return InetAddress.ofLiteral(values.getFirst()).getHostAddress();
        } catch (IllegalArgumentException invalidAddress) {
            return peer;
        }
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
