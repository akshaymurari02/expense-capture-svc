package com.harness.expensecapture.exception;

import org.springframework.http.HttpStatus;

/** The request was well-formed but cannot be carried out with the data available. */
public class UnprocessableException extends ReceiptException {

    private static final long serialVersionUID = 1L;

    public UnprocessableException(final String code, final String message) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public UnprocessableException(final String code, final String message, final Throwable cause) {
        super(code, message, HttpStatus.UNPROCESSABLE_ENTITY, cause);
    }
}
