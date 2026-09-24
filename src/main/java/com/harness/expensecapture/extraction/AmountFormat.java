package com.harness.expensecapture.extraction;

import java.math.BigDecimal;

/** Normalises printed amounts ({@code 17.85}, {@code 1.234,56}, {@code 1,234.56}) into {@link BigDecimal}. */
final class AmountFormat {

    private AmountFormat() {
        throw new AssertionError("No instances.");
    }

    static BigDecimal toBigDecimal(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("amount text must not be blank");
        }
        final String trimmed = raw.trim();
        final int lastDot = trimmed.lastIndexOf('.');
        final int lastComma = trimmed.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            return new BigDecimal(lastComma > lastDot
                    ? trimmed.replace(".", "").replace(',', '.')
                    : trimmed.replace(",", ""));
        }
        if (lastComma >= 0) {
            // A single comma three digits from the end is a thousands separator, otherwise a decimal point.
            final boolean thousands = trimmed.length() - lastComma - 1 == 3;
            return new BigDecimal(thousands ? trimmed.replace(",", "") : trimmed.replace(',', '.'));
        }
        return new BigDecimal(trimmed);
    }
}
