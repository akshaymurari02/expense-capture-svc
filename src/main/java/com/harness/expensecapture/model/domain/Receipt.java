package com.harness.expensecapture.model.domain;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded receipt file. Aggregate root in its own right: a receipt exists before any transaction is
 * derived from it, so it is referenced by id only.
 */
public record Receipt(
        String id,
        StoredFilename filename,
        Path storedPath,
        String contentType,
        long sizeBytes,
        Instant uploadedAt) {

    /** Allocates the id up front so the caller can name the stored file after it before persisting. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static Receipt create(final String id, final StoredFilename filename, final Path storedPath,
            final String contentType, final long sizeBytes) {
        return new Receipt(id, filename, storedPath, contentType, sizeBytes, Instant.now());
    }

    public String originalFilename() {
        return filename.value();
    }

    /** Filename without its extension; used by the stub OCR provider to resolve fixture text. */
    public String filenameStem() {
        return filename.stem();
    }
}
