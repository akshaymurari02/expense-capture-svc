package com.harness.expensecapture.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Chain behaviour: order, fail-fast, and extensibility without touching existing code. */
@DisplayName("Upload validation chain")
class UploadValidationChainTest {

    private static final byte[] CONTENT = "MERCHANT: Cafe Mitte".getBytes(StandardCharsets.UTF_8);

    private ReceiptUpload newUpload() {
        return ReceiptUpload.open(
                new MockMultipartFile("file", "receipt.txt", MediaType.TEXT_PLAIN_VALUE, CONTENT), 8);
    }

    /** Records that it ran, so ordering and short-circuiting are observable. */
    private static final class RecordingValidator implements UploadValidator {
        private final String name;
        private final List<String> log;
        private final boolean reject;

        RecordingValidator(final String name, final List<String> log, final boolean reject) {
            this.name = name;
            this.log = log;
            this.reject = reject;
        }

        @Override
        public void validate(final ReceiptUpload upload) {
            log.add(name);
            if (reject) {
                throw new ValidationException(ErrorCodes.FILE_UNSUPPORTED, name + " rejected");
            }
        }

        @Override
        public String ruleName() {
            return name;
        }
    }

    @Test
    void should_validate_withAllRulesPassing_runEveryRuleInOrder() throws Exception {
        final List<String> executed = new ArrayList<>();
        final UploadValidationChain chain = new UploadValidationChain(List.of(
                new RecordingValidator("first", executed, false),
                new RecordingValidator("second", executed, false)));

        try (ReceiptUpload upload = newUpload()) {
            chain.validate(upload);
        }

        assertThat(executed).containsExactly("first", "second");
    }

    @Test
    void should_validate_withFirstRuleFailing_notRunLaterRules() throws Exception {
        final List<String> executed = new ArrayList<>();
        final UploadValidationChain chain = new UploadValidationChain(List.of(
                new RecordingValidator("cheap", executed, true),
                new RecordingValidator("expensive", executed, false)));

        try (ReceiptUpload upload = newUpload()) {
            assertThatThrownBy(() -> chain.validate(upload)).isInstanceOf(ValidationException.class);
        }

        // The point of cheap-first ordering: an expensive check must not run on an already-rejected payload.
        assertThat(executed).containsExactly("cheap");
    }

    @Test
    void should_validate_withNoRulesRegistered_acceptTheUpload() throws Exception {
        final UploadValidationChain chain = new UploadValidationChain(List.of());

        try (ReceiptUpload upload = newUpload()) {
            chain.validate(upload);
        }

        assertThat(chain.ruleNames()).isEmpty();
    }

    @Test
    void should_construct_withMutableList_notBeAffectedByLaterMutation() throws Exception {
        final List<UploadValidator> mutable = new ArrayList<>();
        mutable.add(new RecordingValidator("only", new ArrayList<>(), false));
        final UploadValidationChain chain = new UploadValidationChain(mutable);

        mutable.clear();

        assertThat(chain.ruleNames()).containsExactly("only");
    }
}
