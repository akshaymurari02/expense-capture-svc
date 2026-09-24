package com.harness.expensecapture.validation;

import com.harness.expensecapture.exception.ValidationException;
import com.harness.expensecapture.util.LogConstants;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Runs every {@link UploadValidator} in order and fails on the first breach.
 *
 * <p>Spring injects the list already sorted by {@code @Order}, so registering a new rule requires no change
 * here. Fail-fast rather than collect-all: the rules are ordered cheap-first precisely so that an expensive
 * check never runs on a payload a cheap one already rejected, and an upload has a single reason to fail.
 */
@Slf4j
@Component
public class UploadValidationChain {

    private final List<UploadValidator> validators;

    public UploadValidationChain(final List<UploadValidator> validators) {
        this.validators = List.copyOf(validators);
        log.info("{} Upload validation chain ready. rules={}", LogConstants.SVC, ruleNames());
    }

    /**
     * @throws ValidationException from the first validator that rejects the upload
     */
    public void validate(final ReceiptUpload upload) {
        for (final UploadValidator validator : validators) {
            try {
                validator.validate(upload);
            } catch (final ValidationException e) {
                log.warn("{} Upload rejected by {}. reason={}", LogConstants.SVC, validator.ruleName(),
                        e.getMessage());
                throw e;
            }
        }
    }

    /** Exposed so a test can assert the ordering contract instead of assuming it. */
    public List<String> ruleNames() {
        return validators.stream().map(UploadValidator::ruleName).toList();
    }
}
