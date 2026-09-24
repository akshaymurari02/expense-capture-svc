package com.harness.expensecapture.health;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.health.HealthDetail.OcrDetail;
import com.harness.expensecapture.ocr.OcrProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks the OCR provider. For the stub that means the fixture directory it reads text from; a real provider
 * would check vendor reachability here instead. A prerequisite for serving: processing cannot run without OCR.
 */
@Component
@RequiredArgsConstructor
public class OcrHealthIndicator implements HealthIndicator {

    private final OcrProvider ocrProvider;
    private final AppProperties properties;

    @Override
    public String name() {
        return "ocr";
    }

    @Override
    public DependencyHealth check() {
        final long startedAt = System.currentTimeMillis();
        final String fixtureDir = properties.getOcr().getFixtureDir();
        try {
            final Path dir = Paths.get(fixtureDir).toAbsolutePath().normalize();
            final boolean readable = Files.isDirectory(dir) && Files.isReadable(dir);
            final OcrDetail detail = new OcrDetail(ocrProvider.providerName(), dir.toString(), readable);
            final long elapsed = System.currentTimeMillis() - startedAt;

            return readable
                    ? DependencyHealth.up(elapsed, detail)
                    : DependencyHealth.down(elapsed, "fixture directory is not readable: " + dir, detail);
        } catch (final RuntimeException e) {
            return DependencyHealth.down(System.currentTimeMillis() - startedAt,
                    "ocr check failed: " + e.getMessage(),
                    new OcrDetail(ocrProvider.providerName(), fixtureDir, false));
        }
    }
}
