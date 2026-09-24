package com.harness.expensecapture.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The aggregate's own override behaviour: one state transition, and it polices its own item identity. */
@DisplayName("Transaction override")
class TransactionOverrideTest {

    private static final String EUR = "EUR";

    private Transaction transaction;

    private LineItem item(final String description, final String amount) {
        return LineItem.create(description, Money.of(amount, EUR));
    }

    @BeforeEach
    void setUp() {
        transaction = Transaction.create("receipt-1");
        transaction.applyExtraction(new StubExtraction(), "raw ocr text", "stub");
    }

    @Test
    void should_overrideLineItems_withValidReplacement_setItemsAndStatusTogether() {
        final LineItem merged = item("Combined", "15.00");

        transaction.overrideLineItems(List.of(merged), ItemizeStatus.COMPLETE, Money.zero(EUR));

        // Both halves of the transition must be visible; neither alone is a legal state.
        assertThat(transaction.getLineItems()).containsExactly(merged);
        assertThat(transaction.getItemizeStatus()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(transaction.getReconciliationGap().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void should_overrideLineItems_withDuplicateIds_rejectAndLeaveItemsUnchanged() {
        final List<LineItem> before = List.copyOf(transaction.getLineItems());
        final LineItem original = before.get(0);
        final LineItem clashing = new LineItem(original.id(), "Clash", Money.of("1.00", EUR), null, null);

        assertThatThrownBy(() -> transaction.overrideLineItems(
                List.of(original, clashing), ItemizeStatus.COMPLETE, Money.zero(EUR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate item_id")
                // The message must explain how to split correctly, not merely that it failed.
                .hasMessageContaining("omit it on the");

        assertThat(transaction.getLineItems()).isEqualTo(before);
    }

    @Test
    void should_overrideLineItems_withNullStatus_rejectRatherThanStoreItems() {
        final List<LineItem> before = List.copyOf(transaction.getLineItems());

        assertThatThrownBy(() -> transaction.overrideLineItems(
                List.of(item("X", "15.00")), null, Money.zero(EUR)))
                .isInstanceOf(NullPointerException.class);

        // The guard runs before the items are swapped in, so a rejected call changes nothing.
        assertThat(transaction.getLineItems()).isEqualTo(before);
    }

    @Test
    void should_ownsAllLineItems_withOwnIds_returnTrue() {
        final List<String> ownIds = transaction.getLineItems().stream().map(LineItem::id).toList();

        assertThat(transaction.ownsAllLineItems(ownIds)).isTrue();
        assertThat(transaction.ownsAllLineItems(List.of())).isTrue();
    }

    @Test
    void should_ownsAllLineItems_withForeignId_returnFalse() {
        final Transaction other = Transaction.create("receipt-2");
        other.applyExtraction(new StubExtraction(), "raw", "stub");
        final String foreignId = other.getLineItems().get(0).id();

        assertThat(transaction.ownsAllLineItems(List.of(foreignId))).isFalse();
        assertThat(transaction.ownsAllLineItems(List.of("does-not-exist"))).isFalse();
    }

    @Test
    void should_getLineItems_returnUnmodifiableView() {
        assertThatThrownBy(() -> transaction.getLineItems().add(item("Sneaky", "1.00")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** A usable header plus three net items totalling 15.00 against a 17.85 gross total. */
    private static final class StubExtraction implements ExtractedFields {
        @Override
        public String merchant() {
            return "Cafe Mitte";
        }

        @Override
        public LocalDate transactionDate() {
            return LocalDate.of(2026, 3, 12);
        }

        @Override
        public String currency() {
            return EUR;
        }

        @Override
        public Money grandTotal() {
            return Money.of("17.85", EUR);
        }

        @Override
        public Money printedSubtotal() {
            return Money.of("15.00", EUR);
        }

        @Override
        public LineItemBasis lineItemBasis() {
            return LineItemBasis.NET;
        }

        @Override
        public List<Tax> taxes() {
            return List.of(Tax.create("VAT", new BigDecimal("0.19"), Money.of("2.85", EUR)));
        }

        @Override
        public List<LineItem> lineItems() {
            return List.of(
                    LineItem.create("Espresso", Money.of("3.50", EUR)),
                    LineItem.create("Sandwich", Money.of("8.90", EUR)),
                    LineItem.create("Mineral water", Money.of("2.60", EUR)));
        }
    }
}
