package com.harness.expensecapture.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.health.HealthDetail.OcrDetail;
import com.harness.expensecapture.health.HealthDetail.StorageDetail;
import com.harness.expensecapture.ocr.OcrProvider;
import com.harness.expensecapture.model.domain.Receipt;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Individual dependency checks, including the failure paths that make the probe useful. */
class HealthIndicatorTest {

    private static final OcrProvider STUB_PROVIDER = new OcrProvider() {
        @Override
        public String extractText(final Receipt receipt) {
            return "unused";
        }

        @Override
        public String providerName() {
            return "stub";
        }
    };

    @Test
    void should_checkStorage_withWritableDirectory_returnUpWithFreeSpace(@TempDir final Path tempDir) {
        final DependencyHealth health = new StorageHealthIndicator(tempDir).check();

        assertThat(health.status()).isEqualTo(HealthStatus.UP);
        assertThat(health.reason()).isNull();
        final StorageDetail detail = (StorageDetail) health.detail();
        assertThat(detail.exists()).isTrue();
        assertThat(detail.writable()).isTrue();
        assertThat(detail.freeSpaceMb()).isPositive();
        assertThat(health.responseTime()).isNotNegative();
    }

    @Test
    void should_checkStorage_withMissingDirectory_returnDownWithReason(@TempDir final Path tempDir) {
        final DependencyHealth health = new StorageHealthIndicator(tempDir.resolve("absent")).check();

        assertThat(health.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(health.reason()).contains("does not exist");
        assertThat(((StorageDetail) health.detail()).exists()).isFalse();
    }

    @Test
    void should_storageIndicator_reportUnderTheStorageKey(@TempDir final Path tempDir) {
        assertThat(new StorageHealthIndicator(tempDir).name()).isEqualTo("storage");
    }

    @Test
    void should_checkOcr_withReadableFixtureDirectory_returnUp() {
        final AppProperties properties = new AppProperties();
        properties.getOcr().setProvider("stub");
        properties.getOcr().setFixtureDir("./fixtures/task-a");

        final DependencyHealth health = new OcrHealthIndicator(STUB_PROVIDER, properties).check();

        assertThat(health.status()).isEqualTo(HealthStatus.UP);
        final OcrDetail detail = (OcrDetail) health.detail();
        assertThat(detail.provider()).isEqualTo("stub");
        assertThat(detail.readable()).isTrue();
    }

    @Test
    void should_checkOcr_withMissingFixtureDirectory_returnDownWithReason() {
        final AppProperties properties = new AppProperties();
        properties.getOcr().setProvider("stub");
        properties.getOcr().setFixtureDir("./does-not-exist");

        final DependencyHealth health = new OcrHealthIndicator(STUB_PROVIDER, properties).check();

        assertThat(health.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(health.reason()).contains("not readable");
        assertThat(((OcrDetail) health.detail()).readable()).isFalse();
    }
}
