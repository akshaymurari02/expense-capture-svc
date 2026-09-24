package com.harness.expensecapture.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base of the service error hierarchy: every failure carries a code, a message and an HTTP status. */
@Getter
public abstract class ReceiptException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final HttpStatus httpStatus;

    protected ReceiptException(final String code, final String message, final HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    protected ReceiptException(final String code, final String message, final HttpStatus httpStatus,
            final Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
