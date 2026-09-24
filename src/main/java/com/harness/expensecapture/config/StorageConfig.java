package com.harness.expensecapture.config;

import com.harness.expensecapture.util.LogConstants;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prepares and verifies the filesystem locations the service depends on. Any problem here is fatal:
 * the service must not start if it cannot persist uploads.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final AppProperties properties;

    @PostConstruct
    void validateStorage() {
        final Path uploadDir = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (final IOException e) {
            throw new IllegalStateException(
                    "Startup failed: cannot create upload directory " + uploadDir + " - " + e.getMessage(), e);
        }
        if (!Files.isWritable(uploadDir)) {
            throw new IllegalStateException("Startup failed: upload directory is not writable: " + uploadDir);
        }
        // OCR provider selection is validated in OcrProviderConfig, which knows what is actually registered.
        log.info("{} Storage validated. uploadDir={} maxFileSize={}", LogConstants.SVC, uploadDir,
                properties.getStorage().getMaxFileSize());
    }

    @Bean
    Path uploadDirectory() {
        return Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
    }
}
