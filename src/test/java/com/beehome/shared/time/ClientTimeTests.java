package com.beehome.shared.time;

import com.beehome.shared.exception.InputException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTimeTests {
    private static final Instant MIDNIGHT = Instant.parse("2026-09-21T00:15:00Z");

    @Test void usesUtcWhenOffsetIsMissing() {
        assertThat(new ClientTime(new MockHttpServletRequest()).date(MIDNIGHT))
                .isEqualTo(LocalDate.parse("2026-09-21"));
    }

    @Test void supportsNegativeAndFractionalOffsetsWithoutSharingRequestState() {
        var west = new MockHttpServletRequest();
        west.addHeader("X-Timezone-Offset", "-180");
        var east = new MockHttpServletRequest();
        east.addHeader("X-Timezone-Offset", "345");
        assertThat(new ClientTime(west).date(MIDNIGHT)).isEqualTo(LocalDate.parse("2026-09-20"));
        assertThat(new ClientTime(east).date(MIDNIGHT)).isEqualTo(LocalDate.parse("2026-09-21"));
    }

    @Test void rejectsMalformedAndOutOfRangeOffsets() {
        for (String value : new String[]{"", "abc", "5.5", "1081", "-1081", "2147483647"}) {
            var request = new MockHttpServletRequest();
            request.addHeader("X-Timezone-Offset", value);
            assertThatThrownBy(() -> new ClientTime(request).date(MIDNIGHT)).isInstanceOf(InputException.class);
        }
    }
}
