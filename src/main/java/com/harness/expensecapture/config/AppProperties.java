package com.harness.expensecapture.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration bound from {@code app.*}. Validated at startup so the service fails fast with a
 * descriptive error rather than silently defaulting.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    @NotNull
    private Storage storage = new Storage();

    @Valid
    @NotNull
    private Ocr ocr = new Ocr();

    @Valid
    @NotNull
    private Reconciliation reconciliation = new Reconciliation();

    @Getter
    @Setter
    public static class Storage {

        /** Directory receipts are written to. Created and write-checked at startup. */
        @NotBlank
        private String uploadDir;

        /** Hard upper bound on a single uploaded file. */
        @NotNull
        private DataSize maxFileSize;

        /** Accepted request content types for upload. */
        @NotEmpty
        private List<String> allowedTypes;
    }

    @Getter
    @Setter
    public static class Ocr {

        /** OCR implementation to use. Only {@code stub} is supported in this exercise. */
        @NotBlank
        private String provider;

        /** Directory holding the fixture text used by the stub provider. */
        @NotBlank
        private String fixtureDir;
    }

    @Getter
    @Setter
    public static class Reconciliation {

        /** Absolute rounding tolerance when comparing money sums, in major currency units. */
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal tolerance;
    }
}
