package com.harness.expensecapture.exception;

import org.springframework.http.HttpStatus;

/** A referenced aggregate does not exist. */
public class NotFoundException extends ReceiptException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(final String code, final String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
}
