package com.harness.expensecapture.model.domain;

/**
 * Whether line item amounts exclude or include tax.
 *
 * <p>This describes the <em>line items</em>, not the receipt's tax treatment. The grand total is always
 * the gross amount the customer paid (tax included) on every fixture, so "inclusive vs exclusive receipt"
 * is not a meaningful distinction. What actually changes the reconciliation arithmetic is the basis of the
 * item amounts.
 */
public enum LineItemBasis {

    /** Item amounts exclude tax: {@code sum(items) + sum(taxes) == grandTotal}. */
    NET,

    /** Item amounts already include tax: {@code sum(items) == grandTotal}. */
    GROSS,

    /** No items were parsed, so no basis can be determined and reconciliation does not apply. */
    UNKNOWN
}
