package com.harness.expensecapture.model.domain;

/** Outcome of comparing auto-itemized line items against the receipt total. */
public enum ItemizeStatus {

    /** Header parsed, items present, and items reconcile with the grand total. */
    COMPLETE,

    /** Header parsed, but items are missing or do not reconcile. Data is kept as-is for a human to fix. */
    NEEDS_REVIEW,

    /** The header itself is unusable (no merchant, date, or total), so nothing can be trusted. */
    FAILED
}
