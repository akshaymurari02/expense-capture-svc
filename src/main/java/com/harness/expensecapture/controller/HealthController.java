package com.harness.expensecapture.controller;

import com.harness.expensecapture.health.HealthAggregator;
import com.harness.expensecapture.health.HealthReport;
import com.harness.expensecapture.health.HealthStatus;
import com.harness.expensecapture.health.HealthStatusMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness and readiness probes.
 *
 * <p>The two are separate on purpose: a failing liveness probe makes an orchestrator restart the container,
 * whereas a failing readiness probe makes it withdraw traffic and leave the container running. If liveness
 * checked dependencies, a brief dependency outage would restart every instance at once and leave no warm
 * capacity when the dependency recovered. Liveness therefore stays deliberately dumb and cheap.
 *
 * <p>Readiness checks only infrastructure prerequisites — storage and OCR here, plus caches, datastores or
 * brokers in a larger service. Ordinary application components are not probed: they are wired at startup and
 * cannot fail independently at runtime.
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthAggregator healthAggregator;
    private final HealthStatusMapper statusMapper;

    /** Liveness: is the process running? No dependency checks, so a slow dependency cannot cause a restart. */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", HealthStatus.UP.name()));
    }

    /** Readiness: are the prerequisites usable, so this instance can serve traffic? */
    @GetMapping("/ready")
    public ResponseEntity<HealthReport> ready() {
        final HealthReport report = healthAggregator.readiness();
        final HttpStatus status = statusMapper.toHttpStatus(report.status());

        return ResponseEntity.status(status).body(report);
    }
}
