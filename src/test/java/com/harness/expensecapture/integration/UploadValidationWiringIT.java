package com.harness.expensecapture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.validation.UploadValidationChain;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The wired chain, not a hand-built one: asserts Spring really injects the validators cheap-first.
 *
 * <p>A unit test can prove the chain honours the order it is given; only the real context can prove the order
 * it is given is correct. Without this, adding a validator and forgetting {@code @Order} would silently put
 * content inspection ahead of the size check.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.upload-dir=./target/test-uploads",
        "app.ocr.fixture-dir=./fixtures/task-a"
})
@DisplayName("Upload validation chain wiring")
class UploadValidationWiringIT {

    @Autowired
    private UploadValidationChain chain;

    @Test
    void should_injectValidators_inMetadataBeforeContentOrder() {
        final List<String> rules = chain.ruleNames();

        assertThat(rules).contains("FileSizeValidator", "DeclaredContentTypeValidator", "FileContentValidator");
        assertThat(rules.indexOf("FileContentValidator"))
                .as("content inspection must run after every metadata check")
                .isGreaterThan(rules.indexOf("FileSizeValidator"))
                .isGreaterThan(rules.indexOf("DeclaredContentTypeValidator"));
    }

    @Test
    void should_registerEveryValidatorOnClasspath_withoutExplicitWiring() {
        // Adding a validator must require no edit here or in the chain: OCP in practice.
        assertThat(chain.ruleNames()).hasSize(3);
    }
}
