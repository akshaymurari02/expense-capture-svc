package com.harness.expensecapture.model.dto;

import com.harness.expensecapture.model.domain.Receipt;
import java.time.Instant;

/** Response for a successful receipt upload. */
public record UploadResponse(
        String receiptId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        Instant uploadedAt) {

    public static UploadResponse from(final Receipt receipt) {
        return new UploadResponse(receipt.id(), receipt.originalFilename(), receipt.contentType(),
                receipt.sizeBytes(), receipt.uploadedAt());
    }
}
