package com.harness.expensecapture.health;

import com.harness.expensecapture.util.LogConstants;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Runs the registered {@link HealthIndicator}s and aggregates them into the readiness report.
 *
 * <p>Every registered indicator is a genuine prerequisite, so any one of them being {@code DOWN} makes the
 * service {@code DOWN}. {@code DEGRADED} is reported but still serves traffic.
 */
@Slf4j
@Service
public class HealthAggregator {

    private static final String UNKNOWN_VERSION = "unknown";
    private static final String DEFAULT_PROFILE = "default";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final List<HealthIndicator> indicators;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final Environment environment;
    private final String serviceName;

    public HealthAggregator(final List<HealthIndicator> indicators,
            final ObjectProvider<BuildProperties> buildProperties, final Environment environment) {
        this.indicators = indicators;
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.serviceName = environment.getProperty("spring.application.name", "expense-capture");
    }

    /** Readiness report: can this instance serve traffic? */
    public HealthReport readiness() {
        final Map<String, DependencyHealth> results = new LinkedHashMap<>();
        for (final HealthIndicator indicator : indicators) {
            results.put(indicator.name(), safeCheck(indicator));
        }
        final HealthStatus overall = aggregate(results);
        logOutcome(overall, results);
        return new HealthReport(overall, serviceName, version(), profile(), uptimeSeconds(), runtime(), results);
    }

    /** Guards against an indicator that throws despite the contract, so one bad check cannot 500 the probe. */
    private DependencyHealth safeCheck(final HealthIndicator indicator) {
        try {
            return indicator.check();
        } catch (final RuntimeException e) {
            log.warn("{} Health indicator '{}' threw unexpectedly.", LogConstants.SVC, indicator.name(), e);
            return DependencyHealth.down(0, "check threw: " + e.getMessage(), null);
        }
    }

    private HealthStatus aggregate(final Map<String, DependencyHealth> results) {
        boolean anyDegraded = false;
        for (final DependencyHealth health : results.values()) {
            if (health.status() == HealthStatus.DOWN) {
                return HealthStatus.DOWN;
            }
            if (health.status() == HealthStatus.DEGRADED) {
                anyDegraded = true;
            }
        }
        return anyDegraded ? HealthStatus.DEGRADED : HealthStatus.UP;
    }

    /** Probes run every few seconds, so a healthy result must not flood the logs. */
    private void logOutcome(final HealthStatus overall, final Map<String, DependencyHealth> results) {
        if (overall == HealthStatus.UP) {
            log.debug("{} Readiness check passed. dependencies={}", LogConstants.SVC, results.keySet());
            return;
        }
        results.forEach((name, health) -> {
            if (health.status() != HealthStatus.UP) {
                log.warn("{} Dependency {} is {}. reason={}", LogConstants.SVC, name, health.status(),
                        health.reason());
            }
        });
    }

    private String version() {
        final BuildProperties build = buildProperties.getIfAvailable();
        return build == null ? UNKNOWN_VERSION : build.getVersion();
    }

    private String profile() {
        final String[] active = environment.getActiveProfiles();
        return active.length == 0 ? DEFAULT_PROFILE : String.join(",", active);
    }

    private long uptimeSeconds() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }

    private HealthReport.Runtime runtime() {
        final var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new HealthReport.Runtime(System.getProperty("java.version"), heap.getUsed() / BYTES_PER_MB,
                heap.getMax() / BYTES_PER_MB);
    }
}
