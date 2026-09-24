package com.harness.expensecapture.health;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Table-driven {@link HealthStatusMapper}. A new {@link HealthStatus} is supported by adding one entry, with
 * no conditional logic to modify anywhere.
 *
 * <p>{@code DEGRADED} maps to 200: the instance is impaired but can still serve, so withdrawing it from the
 * load balancer would remove capacity without fixing anything.
 */
@Component
public class DefaultHealthStatusMapper implements HealthStatusMapper {

    /** Fail safe: an unmapped status must not be treated as healthy. */
    private static final HttpStatus FALLBACK = HttpStatus.SERVICE_UNAVAILABLE;

    private static final Map<HealthStatus, HttpStatus> MAPPING = new EnumMap<>(Map.of(
            HealthStatus.UP, HttpStatus.OK,
            HealthStatus.DEGRADED, HttpStatus.OK,
            HealthStatus.DOWN, HttpStatus.SERVICE_UNAVAILABLE));

    @Override
    public HttpStatus toHttpStatus(final HealthStatus status) {
        return status == null ? FALLBACK : MAPPING.getOrDefault(status, FALLBACK);
    }
}
