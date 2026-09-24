package com.harness.expensecapture.health;

import com.harness.expensecapture.health.HealthDetail.StorageDetail;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks the upload directory the service writes receipts to. A prerequisite for serving: without a writable
 * directory every upload fails.
 */
@Component
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    /** Below this much free space, uploads are at risk and the dependency is reported as degraded. */
    private static final long LOW_SPACE_THRESHOLD_MB = 100;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final Path uploadDirectory;

    @Override
    public String name() {
        return "storage";
    }

    @Override
    public DependencyHealth check() {
        final long startedAt = System.currentTimeMillis();
        try {
            final boolean exists = Files.isDirectory(uploadDirectory);
            final boolean writable = exists && Files.isWritable(uploadDirectory);
            final long freeSpaceMb = exists ? Files.getFileStore(uploadDirectory).getUsableSpace() / BYTES_PER_MB : 0;
            final StorageDetail detail = new StorageDetail(uploadDirectory.toString(), exists, writable, freeSpaceMb);
            final long elapsed = System.currentTimeMillis() - startedAt;

            if (!exists) {
                return DependencyHealth.down(elapsed, "upload directory does not exist", detail);
            }
            if (!writable) {
                return DependencyHealth.down(elapsed, "upload directory is not writable", detail);
            }
            if (freeSpaceMb < LOW_SPACE_THRESHOLD_MB) {
                return DependencyHealth.degraded(elapsed,
                        "low disk space: " + freeSpaceMb + "MB free, threshold " + LOW_SPACE_THRESHOLD_MB + "MB",
                        detail);
            }
            return DependencyHealth.up(elapsed, detail);
        } catch (final IOException | RuntimeException e) {
            return DependencyHealth.down(System.currentTimeMillis() - startedAt,
                    "storage check failed: " + e.getMessage(),
                    new StorageDetail(uploadDirectory.toString(), false, false, 0));
        }
    }
}
