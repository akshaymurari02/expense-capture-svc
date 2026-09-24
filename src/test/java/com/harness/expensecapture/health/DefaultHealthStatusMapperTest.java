package com.harness.expensecapture.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Status-to-HTTP mapping, including the fail-safe for an unmapped status. */
class DefaultHealthStatusMapperTest {

    private final HealthStatusMapper mapper = new DefaultHealthStatusMapper();

    @Test
    void should_toHttpStatus_withUp_returnOk() {
        assertThat(mapper.toHttpStatus(HealthStatus.UP)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_toHttpStatus_withDegraded_returnOkBecauseItCanStillServe() {
        assertThat(mapper.toHttpStatus(HealthStatus.DEGRADED)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_toHttpStatus_withDown_returnServiceUnavailable() {
        assertThat(mapper.toHttpStatus(HealthStatus.DOWN)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void should_toHttpStatus_withNull_returnServiceUnavailableRatherThanOk() {
        // Fail safe: an unknown status must never be reported as healthy.
        assertThat(mapper.toHttpStatus(null)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void should_toHttpStatus_withEveryDeclaredStatus_returnAMapping() {
        // Guards against a new enum constant silently falling through to the fallback unnoticed.
        for (final HealthStatus status : HealthStatus.values()) {
            assertThat(mapper.toHttpStatus(status)).isNotNull();
        }
    }
}
