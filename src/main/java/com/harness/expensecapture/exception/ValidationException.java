package com.harness.expensecapture.exception;

import org.springframework.http.HttpStatus;

/** Input failed validation at the request boundary. */
public class ValidationException extends ReceiptException {

    private static final long serialVersionUID = 1L;

    public ValidationException(final String code, final String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
