package com.harness.expensecapture.validation;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rejects a payload larger than the configured limit, before anything reads its content. */
@Component
@Order(UploadValidator.ORDER_METADATA)
@RequiredArgsConstructor
public class FileSizeValidator implements UploadValidator {

    private final AppProperties properties;

    @Override
    public void validate(final ReceiptUpload upload) {
        final long maxBytes = properties.getStorage().getMaxFileSize().toBytes();
        if (upload.sizeBytes() > maxBytes) {
            throw new ValidationException(ErrorCodes.FILE_UNSUPPORTED,
                    "File exceeds the maximum allowed size of " + maxBytes + " bytes");
        }
    }
}
