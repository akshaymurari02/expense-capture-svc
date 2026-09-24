package com.harness.expensecapture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.ocr.OcrProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves a second {@link OcrProvider} can coexist and be chosen by configuration.
 *
 * <p>Before provider selection existed, merely putting a second implementation on the classpath broke startup
 * with {@code expected single matching bean but found 2}, so the documented extension point did not work. This
 * test registers a fake vendor provider, points {@code app.ocr.provider} at it, and asserts it is the one
 * injected — supporting a new file type or vendor is a new class plus one property, with no service edits.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.upload-dir=./target/test-uploads",
        "app.ocr.fixture-dir=./fixtures/task-a",
        "app.ocr.provider=fake-vendor"
})
@DisplayName("OCR provider selection")
class OcrProviderSelectionIT {

    static final String FAKE_TEXT = "MERCHANT: Fake Vendor\nDATE: 2026-03-12\nCURRENCY: EUR\nTOTAL 9.99";

    @TestConfiguration
    static class FakeVendorConfig {

        @Bean
        OcrProvider fakeVendorOcrProvider() {
            return new OcrProvider() {
                @Override
                public String extractText(final Receipt receipt) {
                    return FAKE_TEXT;
                }

                @Override
                public String providerName() {
                    return "fake-vendor";
                }
            };
        }
    }

    @Autowired
    private OcrProvider activeProvider;

    @Test
    void should_selectProvider_matchingConfiguredName_whenSeveralAreRegistered() {
        assertThat(activeProvider.providerName()).isEqualTo("fake-vendor");
    }

    @Test
    void should_startContext_withTwoProvidersOnTheClasspath() {
        // The stub is still a bean; selection, not exclusion, is what resolves the ambiguity.
        assertThat(activeProvider).isNotNull();
        assertThat(activeProvider.extractText(null)).isEqualTo(FAKE_TEXT);
    }
}
