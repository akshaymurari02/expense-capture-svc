package com.harness.expensecapture.extraction;

import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.Money;

/**
 * Result of comparing line items against the receipt total.
 *
 * @param expected  the receipt's own grand total, never recomputed
 * @param actual    what the line items (plus taxes, when items are net) actually add up to
 * @param difference {@code expected - actual}, or {@code null} when the comparison does not apply
 */
public record ReconciliationOutcome(
        ItemizeStatus status,
        Money expected,
        Money actual,
        Money difference) {

    public boolean reconciled() {
        return status == ItemizeStatus.COMPLETE;
    }
}
