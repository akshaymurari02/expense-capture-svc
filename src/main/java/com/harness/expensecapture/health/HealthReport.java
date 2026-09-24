package com.harness.expensecapture.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Aggregated health report. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record HealthReport(
        HealthStatus status,
        String service,
        String version,
        String profile,
        long uptimeSeconds,
        Runtime runtime,
        Map<String, DependencyHealth> dependencies) {

    public record Runtime(String javaVersion, long heapUsedMb, long heapMaxMb) {
    }
}
