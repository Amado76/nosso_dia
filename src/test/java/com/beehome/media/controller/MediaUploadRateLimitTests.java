package com.beehome.media.controller;

import com.beehome.media.security.MediaUploadRateLimitFilter;
import com.beehome.auth.security.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import java.time.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MediaUploadRateLimitTests {
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void ordinaryUploadDoesNotWritePerRequestInfoLog(CapturedOutput output) throws Exception {
        var filter = new MediaUploadRateLimitFilter(10, Clock.systemUTC(), mock(SecurityErrorHandler.class));

        filter.doFilter(request(), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(output.getOut()).doesNotContain("Media upload request finished");
    }

    @Test void rejectsConcurrentUploadBeforeParsingAndReleasesSlot() throws Exception {
        var errors = mock(SecurityErrorHandler.class);
        var filter = new MediaUploadRateLimitFilter(10, 1, Clock.systemUTC(), errors);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        FilterChain blocking = (request, response) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("upload did not finish"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        };
        var first = new Thread(() -> {
            try { filter.doFilter(request(), new MockHttpServletResponse(), blocking); }
            catch (Throwable e) { failure.set(e); }
        });
        first.start();
        try {
            org.assertj.core.api.Assertions.assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            filter.doFilter(request(), new MockHttpServletResponse(), mock(FilterChain.class));
            verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.media.rate-limited"));
        } finally {
            release.countDown();
            first.join(5000);
        }
        org.assertj.core.api.Assertions.assertThat(failure.get()).isNull();
        var after = mock(FilterChain.class);
        filter.doFilter(request(), new MockHttpServletResponse(), after);
        verify(after).doFilter(any(), any());
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/families/00000000-0000-0000-0000-000000000001/media");
    }
    @Test void limitsUploadsBeforeMultipartParsingByRemoteAddressAndFamily() throws Exception {
        var errors = mock(SecurityErrorHandler.class);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        var filter = new MediaUploadRateLimitFilter(2, clock, errors);
        var chain = mock(FilterChain.class);
        for (int attempt = 0; attempt < 3; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/families/00000000-0000-0000-0000-000000000001/media");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "192.0.2." + attempt);
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(2)).doFilter(any(), any());
        verify(errors).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), eq("RATE_LIMITED"), eq("error.media.rate-limited"));
    }
}
