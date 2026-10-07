package com.beehome.shared.time;

import com.beehome.shared.exception.InputException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** Device offset affects calendar dates only, never persisted event timestamps. */
@Component
@RequestScope
public class ClientTime {
    private final HttpServletRequest request;

    public ClientTime(HttpServletRequest request) { this.request = request; }

    public LocalDate today(Clock clock) { return date(clock.instant()); }

    public LocalDate date(Instant instant) {
        return LocalDate.ofInstant(instant, offset());
    }

    private ZoneOffset offset() {
        String value = request.getHeader("X-Timezone-Offset");
        if (value == null) return ZoneOffset.UTC;
        try {
            int minutes = Integer.parseInt(value);
            if (minutes < -1080 || minutes > 1080) throw new InputException();
            return ZoneOffset.ofTotalSeconds(minutes * 60);
        } catch (NumberFormatException exception) {
            throw new InputException();
        }
    }
}
