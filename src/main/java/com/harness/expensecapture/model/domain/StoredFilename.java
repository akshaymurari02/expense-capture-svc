package com.harness.expensecapture.model.domain;

import java.util.Objects;

/**
 * A filename that is safe to use as a path segment.
 *
 * <p>{@code MultipartFile#getOriginalFilename()} is client-supplied and hostile: a value such as
 * {@code ../../../../etc/passwd} would escape the upload directory when resolved. Sanitising into a distinct
 * type means a method that accepts a {@code StoredFilename} <em>cannot</em> be handed a raw client string —
 * the compiler enforces what a comment would only ask for.
 */
public record StoredFilename(String value) {

    private static final int MAX_LENGTH = 120;
    private static final String FALLBACK = "receipt";
    private static final String UNSAFE_CHARS = "[^A-Za-z0-9._-]";

    public StoredFilename {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    /** Sanitises a client-supplied filename. Never throws: an unusable name degrades to {@code receipt}. */
    public static StoredFilename from(final String clientSupplied) {
        if (clientSupplied == null || clientSupplied.isBlank()) {
            return new StoredFilename(FALLBACK);
        }
        // Strip every path component, on either separator, before touching anything else.
        final String withoutPath = clientSupplied.replace('\\', '/');
        final String lastSegment = withoutPath.substring(withoutPath.lastIndexOf('/') + 1);
        final String cleaned = lastSegment.replaceAll(UNSAFE_CHARS, "_");
        final String truncated = cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
        // "." and ".." survive the character filter but are still traversal-relevant.
        if (truncated.isBlank() || ".".equals(truncated) || "..".equals(truncated)) {
            return new StoredFilename(FALLBACK);
        }
        return new StoredFilename(truncated);
    }

    /** Filename without its extension; the stub OCR provider resolves fixture text by this stem. */
    public String stem() {
        final int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    @Override
    public String toString() {
        return value;
    }
}
