package com.harness.expensecapture.config;

import com.harness.expensecapture.ocr.OcrProvider;
import com.harness.expensecapture.util.LogConstants;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Selects the active {@link OcrProvider} from {@code app.ocr.provider}.
 *
 * <p>Without this, a second implementation on the classpath makes every injection point ambiguous and the
 * application fails to start with {@code expected single matching bean but found 2} — so the documented
 * extension point would not actually work. Providers are keyed on their own {@link
 * OcrProvider#providerName()}, so adding a vendor-backed provider means adding a class and setting one
 * property: no edit here, and no service touched.
 */
@Slf4j
@Configuration
public class OcrProviderConfig {

    @Bean
    @Primary
    OcrProvider activeOcrProvider(final List<OcrProvider> available, final AppProperties properties) {
        final String requested = properties.getOcr().getProvider().trim().toLowerCase(Locale.ROOT);
        final Map<String, OcrProvider> byName = available.stream()
                .collect(Collectors.toMap(
                        provider -> provider.providerName().toLowerCase(Locale.ROOT),
                        Function.identity()));

        final OcrProvider selected = byName.get(requested);
        if (selected == null) {
            // Fail fast at startup rather than at the first upload.
            throw new IllegalStateException("Startup failed: unknown app.ocr.provider '"
                    + properties.getOcr().getProvider() + "'. Available: " + byName.keySet());
        }
        log.info("{} OCR provider selected. provider={} available={}", LogConstants.SVC,
                selected.providerName(), byName.keySet());
        return selected;
    }
}
