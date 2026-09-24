package com.harness.expensecapture.health;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outcome of one dependency check.
 *
 * @param responseTime how long the check took, in milliseconds
 * @param reason       why the dependency is not {@link HealthStatus#UP}; {@code null} when healthy
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DependencyHealth(
        HealthStatus status,
        long responseTime,
        String reason,
        HealthDetail detail) {

    public static DependencyHealth up(final long responseTime, final HealthDetail detail) {
        return new DependencyHealth(HealthStatus.UP, responseTime, null, detail);
    }

    public static DependencyHealth degraded(final long responseTime, final String reason,
            final HealthDetail detail) {
        return new DependencyHealth(HealthStatus.DEGRADED, responseTime, reason, detail);
    }

    public static DependencyHealth down(final long responseTime, final String reason, final HealthDetail detail) {
        return new DependencyHealth(HealthStatus.DOWN, responseTime, reason, detail);
    }
}
