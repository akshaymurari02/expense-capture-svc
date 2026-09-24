package com.harness.expensecapture.validation;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rejects a content type outside the configured allowlist.
 *
 * <p>This checks only what the client <em>claims</em>. It is a cheap filter, not a security control —
 * {@link FileContentValidator} is what verifies the claim against the bytes.
 */
@Component
@Order(UploadValidator.ORDER_METADATA)
@RequiredArgsConstructor
public class DeclaredContentTypeValidator implements UploadValidator {

    private final AppProperties properties;

    @Override
    public void validate(final ReceiptUpload upload) {
        final String declared = upload.normalisedContentType();
        if (!properties.getStorage().getAllowedTypes().contains(declared)) {
            throw new ValidationException(ErrorCodes.FILE_UNSUPPORTED,
                    "Unsupported content type '" + declared + "'. Allowed: "
                            + properties.getStorage().getAllowedTypes());
        }
    }
}
