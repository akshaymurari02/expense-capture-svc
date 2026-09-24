package com.harness.expensecapture.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.ocr.OcrProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Provider selection: exact match, case tolerance, and fail-fast on an unknown name. */
@DisplayName("OcrProviderConfig")
class OcrProviderConfigTest {

    private final OcrProviderConfig config = new OcrProviderConfig();
    private AppProperties properties;

    private static OcrProvider provider(final String name) {
        return new OcrProvider() {
            @Override
            public String extractText(final Receipt receipt) {
                return name + " text";
            }

            @Override
            public String providerName() {
                return name;
            }
        };
    }

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
    }

    @Test
    void should_activeOcrProvider_withConfiguredName_returnMatchingProvider() {
        properties.getOcr().setProvider("vendor");

        final OcrProvider selected = config.activeOcrProvider(
                List.of(provider("stub"), provider("vendor")), properties);

        assertThat(selected.providerName()).isEqualTo("vendor");
    }

    @Test
    void should_activeOcrProvider_withDifferentCaseAndWhitespace_stillMatch() {
        properties.getOcr().setProvider("  STUB  ");

        assertThat(config.activeOcrProvider(List.of(provider("stub")), properties).providerName())
                .isEqualTo("stub");
    }

    @Test
    void should_activeOcrProvider_withUnknownName_failFastListingAvailable() {
        properties.getOcr().setProvider("does-not-exist");

        assertThatThrownBy(() -> config.activeOcrProvider(List.of(provider("stub")), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown app.ocr.provider")
                .hasMessageContaining("does-not-exist")
                // The operator needs to know what they could have written instead.
                .hasMessageContaining("stub");
    }

    @Test
    void should_activeOcrProvider_withNoProvidersRegistered_failFast() {
        properties.getOcr().setProvider("stub");

        assertThatThrownBy(() -> config.activeOcrProvider(List.of(), properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown app.ocr.provider");
    }
}
