package com.harness.expensecapture.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Standard error body. Never carries a stack trace or internal detail. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        MismatchDetail mismatch) {

    public static ErrorResponse of(final int status, final String error, final String code, final String message,
            final String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, null);
    }

    public static ErrorResponse withMismatch(final int status, final String error, final String code,
            final String message, final String path, final MismatchDetail mismatch) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, mismatch);
    }
}
