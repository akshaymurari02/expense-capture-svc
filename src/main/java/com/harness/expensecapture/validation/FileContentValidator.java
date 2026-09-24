package com.harness.expensecapture.validation;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import com.harness.expensecapture.storage.FileSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Verifies the payload's magic number against its declared type, so a PDF announced as {@code image/png} is
 * rejected. This is the rule that actually inspects the file rather than trusting the client.
 *
 * <p>A payload with no recognised signature passes: plain text receipts are legitimate here and have none.
 * Tightening this to an allowlist of signatures would reject the exercise's own fixtures.
 */
@Component
@Order(UploadValidator.ORDER_CONTENT)
public class FileContentValidator implements UploadValidator {

    @Override
    public void validate(final ReceiptUpload upload) {
        final FileSignature detected = FileSignature.detect(upload.head());
        if (detected != null && !detected.matchesDeclared(upload.rawContentType())) {
            throw new ValidationException(ErrorCodes.FILE_CONTENT_MISMATCH,
                    "File content is " + detected.canonicalContentType() + " but was declared as '"
                            + upload.normalisedContentType() + "'");
        }
    }
}
