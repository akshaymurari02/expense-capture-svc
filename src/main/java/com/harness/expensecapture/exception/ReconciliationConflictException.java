package com.harness.expensecapture.exception;

import com.harness.expensecapture.model.dto.MismatchDetail;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A user-supplied set of line items does not reconcile with the stored grand total and taxes.
 *
 * <p>This is the hard half of the reconciliation invariant: a human assertion that is arithmetically false
 * is rejected with 409 and nothing is written. Totals are never silently adjusted to make the sums agree.
 */
@Getter
public class ReconciliationConflictException extends ReceiptException {

    private static final long serialVersionUID = 1L;

    private final MismatchDetail mismatch;

    public ReconciliationConflictException(final String message, final MismatchDetail mismatch) {
        super(ErrorCodes.RECONCILIATION_MISMATCH, message, HttpStatus.CONFLICT);
        this.mismatch = mismatch;
    }
}
