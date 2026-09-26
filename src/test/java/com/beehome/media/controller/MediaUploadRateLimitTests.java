package com.beehome.media.controller;

import com.beehome.media.security.MediaUploadRateLimitFilter;
import com.beehome.auth.security.SecurityErrorHandler;
import jakarta.servlet.FilterChain;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;
import static org.mockito.Mockito.*;

class MediaUploadRateLimitTests {
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
