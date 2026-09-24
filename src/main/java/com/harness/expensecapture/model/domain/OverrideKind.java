package com.harness.expensecapture.model.domain;

/**
 * The three user-override verbs the brief names, derived from how the item count changed.
 *
 * <p>Deliberately <em>not</em> a field in the request. The client already expresses its intent by sending the
 * full replacement list, so asking it to also declare the verb would let the two disagree — and then the server
 * would have to decide which to believe. Deriving it keeps one source of truth and makes the override
 * traceable in logs.
 */
public enum OverrideKind {

    /** Same number of items: amounts or descriptions changed. */
    EDIT,

    /** Fewer items than before: several were combined. */
    MERGE,

    /** More items than before: one was divided. */
    SPLIT;

    public static OverrideKind of(final int previousCount, final int newCount) {
        if (newCount < previousCount) {
            return MERGE;
        }
        return newCount > previousCount ? SPLIT : EDIT;
    }
}
