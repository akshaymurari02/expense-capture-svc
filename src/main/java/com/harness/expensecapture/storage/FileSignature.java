package com.harness.expensecapture.storage;

import java.util.Arrays;
import java.util.Locale;

/**
 * Identifies a file by its leading bytes.
 *
 * <p>A declared {@code Content-Type} is client-supplied metadata, so {@code image/png} on its own proves
 * nothing about the payload. Matching the magic number is the only cheap check that looks at what was
 * actually uploaded.
 */
public enum FileSignature {

    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'});

    private final String canonicalContentType;
    private final byte[] magic;

    FileSignature(final String canonicalContentType, final byte[] magic) {
        this.canonicalContentType = canonicalContentType;
        this.magic = magic;
    }

    public String canonicalContentType() {
        return canonicalContentType;
    }

    /** Longest magic number across all signatures; how many bytes a caller must read to identify any type. */
    public static int maxMagicLength() {
        int max = 0;
        for (final FileSignature signature : values()) {
            max = Math.max(max, signature.magic.length);
        }
        return max;
    }

    /** The signature matching these leading bytes, or {@code null} when none does (e.g. plain text). */
    public static FileSignature detect(final byte[] head) {
        if (head == null) {
            return null;
        }
        for (final FileSignature signature : values()) {
            if (head.length >= signature.magic.length
                    && Arrays.equals(head, 0, signature.magic.length, signature.magic, 0, signature.magic.length)) {
                return signature;
            }
        }
        return null;
    }

    /**
     * Whether a declared content type is consistent with this detected signature. {@code jpg} and
     * {@code octet-stream} are tolerated: browsers and curl send both for legitimate uploads.
     */
    public boolean matchesDeclared(final String declaredContentType) {
        if (declaredContentType == null || declaredContentType.isBlank()) {
            return false;
        }
        final String declared = declaredContentType.toLowerCase(Locale.ROOT);
        if ("application/octet-stream".equals(declared)) {
            return true;
        }
        if (this == JPEG) {
            return "image/jpeg".equals(declared) || "image/jpg".equals(declared);
        }
        return canonicalContentType.equals(declared);
    }
}
