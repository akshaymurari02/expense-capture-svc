package com.harness.expensecapture.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

/** Aggregation rules and isolation of misbehaving indicators. */
class HealthAggregatorTest {

    /** Reports no build info, mirroring a build without the build-info goal. */
    private static final ObjectProvider<BuildProperties> NO_BUILD_INFO = new ObjectProvider<>() {
        @Override
        public BuildProperties getObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BuildProperties getObject(final Object... args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BuildProperties getIfAvailable() {
            return null;
        }

        @Override
        public BuildProperties getIfUnique() {
            return null;
        }
    };

    private HealthAggregator aggregatorOf(final HealthIndicator... indicators) {
        return new HealthAggregator(List.of(indicators), NO_BUILD_INFO, new MockEnvironment());
    }

    private HealthIndicator indicator(final String name, final DependencyHealth result) {
        return new HealthIndicator() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public DependencyHealth check() {
                return result;
            }
        };
    }

    @Test
    void should_readiness_withAllPrerequisitesUp_returnUp() {
        final HealthReport report = aggregatorOf(
                indicator("storage", DependencyHealth.up(1, null)),
                indicator("ocr", DependencyHealth.up(1, null))).readiness();

        assertThat(report.status()).isEqualTo(HealthStatus.UP);
        assertThat(report.dependencies()).containsOnlyKeys("storage", "ocr");
    }

    @Test
    void should_readiness_withOnePrerequisiteDown_returnDown() {
        final HealthReport report = aggregatorOf(
                indicator("storage", DependencyHealth.down(1, "not writable", null)),
                indicator("ocr", DependencyHealth.up(1, null))).readiness();

        assertThat(report.status()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void should_readiness_withDegradedPrerequisite_returnDegradedAndStillServe() {
        final HealthReport report = aggregatorOf(
                indicator("storage", DependencyHealth.degraded(1, "low disk space", null))).readiness();

        assertThat(report.status()).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void should_readiness_withDownTakingPrecedenceOverDegraded_returnDown() {
        final HealthReport report = aggregatorOf(
                indicator("storage", DependencyHealth.degraded(1, "low disk space", null)),
                indicator("ocr", DependencyHealth.down(1, "unreachable", null))).readiness();

        assertThat(report.status()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void should_readiness_withThrowingIndicator_reportItDownWithoutFailingTheProbe() {
        final HealthIndicator broken = new HealthIndicator() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public DependencyHealth check() {
                throw new IllegalStateException("boom");
            }
        };

        final HealthReport report = aggregatorOf(indicator("storage", DependencyHealth.up(1, null)), broken)
                .readiness();

        // One misbehaving indicator must not hide the state of the others.
        assertThat(report.dependencies().get("broken").status()).isEqualTo(HealthStatus.DOWN);
        assertThat(report.dependencies().get("broken").reason()).contains("boom");
        assertThat(report.dependencies().get("storage").status()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void should_readiness_withNoBuildInfo_returnUnknownVersionAndDefaultProfile() {
        final HealthReport report = aggregatorOf(indicator("storage", DependencyHealth.up(1, null))).readiness();

        assertThat(report.version()).isEqualTo("unknown");
        assertThat(report.profile()).isEqualTo("default");
        assertThat(report.runtime().javaVersion()).isNotBlank();
        assertThat(report.uptimeSeconds()).isNotNegative();
    }
}
