package com.harness.expensecapture.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Proves the OCP claim concretely: a brand-new rule participates by implementing the interface, with no edit
 * to {@link UploadValidationChain}, {@link ReceiptUpload}, or any existing validator.
 *
 * <p>Uses the rule the brief actually motivates — an upload that OCR could never read.
 */
@DisplayName("Adding a new upload rule")
class NewValidatorExtensibilityTest {

    /** A rule that did not exist when the chain was written. */
    private static final class MinimumContentLengthValidator implements UploadValidator {
        private final long minimumBytes;

        MinimumContentLengthValidator(final long minimumBytes) {
            this.minimumBytes = minimumBytes;
        }

        @Override
        public void validate(final ReceiptUpload upload) {
            if (upload.sizeBytes() < minimumBytes) {
                throw new ValidationException(ErrorCodes.FILE_UNSUPPORTED,
                        "Receipt is too small to contain readable text");
            }
        }
    }

    @Test
    void should_validate_withNewRuleAppended_enforceItWithoutChangingTheChain() throws Exception {
        final UploadValidationChain chain = new UploadValidationChain(
                List.of(new MinimumContentLengthValidator(100)));
        final MockMultipartFile tiny = new MockMultipartFile("file", "receipt.txt",
                MediaType.TEXT_PLAIN_VALUE, "x".getBytes(StandardCharsets.UTF_8));

        try (ReceiptUpload upload = ReceiptUpload.open(tiny, 8)) {
            assertThatThrownBy(() -> chain.validate(upload))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("too small");
        }
        assertThat(chain.ruleNames()).containsExactly("MinimumContentLengthValidator");
    }
}
