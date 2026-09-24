package com.harness.expensecapture.exception;

/** Error codes in {@code [SVC]-[TYPE]-[NUM]} format. Service abbreviation is {@code EXP}. */
public final class ErrorCodes {

    public static final String FILE_MISSING = "EXP-VAL-001";
    public static final String FILE_UNSUPPORTED = "EXP-VAL-002";
    public static final String FILE_CONTENT_MISMATCH = "EXP-VAL-009";
    public static final String INVALID_ITEM_PAYLOAD = "EXP-VAL-003";
    public static final String UNPARSEABLE_RECEIPT = "EXP-VAL-004";
    public static final String NO_STORED_OCR = "EXP-VAL-005";
    public static final String MALFORMED_BODY = "EXP-VAL-006";
    public static final String METHOD_NOT_ALLOWED = "EXP-VAL-007";
    public static final String UNSUPPORTED_MEDIA_TYPE = "EXP-VAL-008";

    public static final String RECEIPT_NOT_FOUND = "EXP-NOT-001";
    public static final String TRANSACTION_NOT_FOUND = "EXP-NOT-002";
    public static final String RESOURCE_NOT_FOUND = "EXP-NOT-003";

    public static final String RECONCILIATION_MISMATCH = "EXP-CON-001";

    public static final String OCR_FAILED = "EXP-EXT-001";
    public static final String STORAGE_FAILED = "EXP-EXT-002";

    public static final String INTERNAL_ERROR = "EXP-INT-001";

    private ErrorCodes() {
        throw new AssertionError("No instances.");
    }
}
