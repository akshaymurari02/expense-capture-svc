package com.harness.expensecapture.health;

import org.springframework.http.HttpStatus;

/**
 * Maps a {@link HealthStatus} to the HTTP status a probe should see.
 *
 * <p>Extracted so the controller holds no branching on status. Introducing a new {@code HealthStatus} means
 * extending the mapping, not editing the controller.
 */
public interface HealthStatusMapper {

    HttpStatus toHttpStatus(HealthStatus status);
}
