package com.harness.expensecapture.model.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A single tax row parsed from a receipt. Taxes are stored as their own records rather than a single
 * number on the transaction header, because a receipt can legitimately carry several rates.
 *
 * <p>Member of the {@link Transaction} aggregate; never persisted or queried independently.
 */
public record Tax(String id, String name, BigDecimal rate, Money amount, String jurisdiction) {

    public Tax {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tax name must not be blank");
        }
        if (rate != null && rate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("tax rate must not be negative");
        }
    }

    public static Tax create(final String name, final BigDecimal rate, final Money amount) {
        return new Tax(UUID.randomUUID().toString(), name, rate, amount, null);
    }
}
