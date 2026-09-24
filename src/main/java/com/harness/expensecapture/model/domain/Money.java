package com.harness.expensecapture.model.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable money value object. Always scale 2, HALF_UP.
 *
 * <p>Never use {@code double} for receipt arithmetic: {@code 4.00 + 6.00 + 1.90 == 11.90} is not
 * reliably true in binary floating point, and neither is {@code 15.00 * 0.19 == 2.85}. Equality is by
 * value via {@link BigDecimal#compareTo} rather than {@code equals}, because {@code 2.85} and
 * {@code 2.850} are not {@code equals}.
 */
public record Money(BigDecimal amount, String currency) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        amount = amount.setScale(SCALE, ROUNDING);
        currency = currency.toUpperCase();
    }

    public static Money of(final BigDecimal amount, final String currency) {
        return new Money(amount, currency);
    }

    public static Money of(final String amount, final String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(final String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(final Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(final Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /** True when the absolute difference between the two amounts is within {@code tolerance}. */
    public boolean equalsWithin(final Money other, final BigDecimal tolerance) {
        requireSameCurrency(other);
        Objects.requireNonNull(tolerance, "tolerance must not be null");
        return amount.subtract(other.amount).abs().compareTo(tolerance.abs()) <= 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    private void requireSameCurrency(final Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }
}
