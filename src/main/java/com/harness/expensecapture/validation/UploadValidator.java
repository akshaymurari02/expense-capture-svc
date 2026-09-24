package com.harness.expensecapture.validation;

import com.harness.expensecapture.exception.ValidationException;

/**
 * One rule an upload must satisfy. Implementations are stateless, do no I/O, and are discovered by Spring, so
 * adding a rule means adding a class — no existing code changes (OCP).
 *
 * <p>Ordering is deliberate and cheap-first: rules that read only metadata run before rules that inspect
 * bytes, so a 2 GB payload is rejected on size without anything looking at its content. Implementations
 * declare their position with {@link org.springframework.core.annotation.Order} using the constants below.
 */
public interface UploadValidator {

    /** Metadata-only checks: size, declared type. */
    int ORDER_METADATA = 100;

    /** Content inspection: magic bytes. Runs only after metadata checks pass. */
    int ORDER_CONTENT = 200;

    /**
     * @throws ValidationException when the upload breaks this rule
     */
    void validate(ReceiptUpload upload);

    /** Used in logs and tests to identify which rule rejected an upload. */
    default String ruleName() {
        return getClass().getSimpleName();
    }
}
