package com.harness.expensecapture.model.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One auto-itemized or user-supplied line of a receipt.
 *
 * <p>Member of the {@link Transaction} aggregate. Items are derived, mutable data: the parser proposes
 * them and the user may replace them. They are never treated as authoritative over the grand total.
 */
public record LineItem(String id, String description, Money amount, Money taxAmount, BigDecimal quantity) {

    public LineItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("line item description must not be blank");
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("line item quantity must be positive when present");
        }
    }

    /** Allocates an id for a genuinely new item, so callers can preserve an existing one instead. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static LineItem create(final String description, final Money amount) {
        return new LineItem(newId(), description, amount, null, null);
    }

    public static LineItem create(final String description, final Money amount, final Money taxAmount,
            final BigDecimal quantity) {
        return new LineItem(newId(), description, amount, taxAmount, quantity);
    }
}
