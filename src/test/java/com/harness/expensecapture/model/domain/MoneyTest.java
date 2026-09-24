package com.harness.expensecapture.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Money arithmetic must be exact; these cases fail if {@code double} is ever reintroduced. */
class MoneyTest {

    private static final String EUR = "EUR";

    @Test
    void should_add_withReceiptAmounts_returnExactSum() {
        final Money sum = Money.of("4.00", EUR).add(Money.of("6.00", EUR)).add(Money.of("1.90", EUR));

        assertThat(sum.amount()).isEqualByComparingTo("11.90");
    }

    @Test
    void should_construct_withMoreThanTwoDecimals_roundHalfUpToScaleTwo() {
        assertThat(Money.of("3.8319327", EUR).amount()).isEqualByComparingTo("3.83");
        assertThat(Money.of("2.845", EUR).amount()).isEqualByComparingTo("2.85");
    }

    @Test
    void should_subtract_withMismatchedItems_returnGap() {
        final Money gap = Money.of("18.50", EUR).subtract(Money.of("11.90", EUR));

        assertThat(gap.amount()).isEqualByComparingTo("6.60");
    }

    @Test
    void should_equalsWithin_withOneCentDrift_returnTrue() {
        assertThat(Money.of("17.85", EUR).equalsWithin(Money.of("17.86", EUR), new BigDecimal("0.01"))).isTrue();
    }

    @Test
    void should_equalsWithin_withTwoCentDrift_returnFalse() {
        assertThat(Money.of("17.85", EUR).equalsWithin(Money.of("17.87", EUR), new BigDecimal("0.01"))).isFalse();
    }

    @Test
    void should_add_withDifferentCurrencies_throwIllegalArgument() {
        assertThatThrownBy(() -> Money.of("1.00", EUR).add(Money.of("1.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void should_construct_withBlankCurrency_throwIllegalArgument() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.00"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_compareScaleDifferences_treatTwoPointEightFiveZeroAsEqual() {
        // BigDecimal.equals would say false here; value comparison must say true.
        assertThat(Money.of("2.850", EUR).amount()).isEqualByComparingTo(new BigDecimal("2.85"));
    }
}
