package com.harness.expensecapture.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import com.harness.expensecapture.model.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reconciliation status decisions, including the rule that no balancing line is ever produced. */
class ReconciliationServiceTest {

    private static final String EUR = "EUR";

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        final AppProperties properties = new AppProperties();
        properties.getReconciliation().setTolerance(new BigDecimal("0.01"));
        service = new ReconciliationService(properties);
    }

    private Tax vat(final String amount) {
        return Tax.create("VAT", new BigDecimal("0.19"), Money.of(amount, EUR));
    }

    private LineItem item(final String description, final String amount) {
        return LineItem.create(description, Money.of(amount, EUR));
    }

    @Test
    void should_evaluate_withNetItemsThatSumWithTax_returnComplete() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("17.85", EUR), LineItemBasis.NET,
                List.of(vat("2.85")),
                List.of(item("Espresso", "3.50"), item("Sandwich", "8.90"), item("Mineral water", "2.60")), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(outcome.actual().amount()).isEqualByComparingTo("17.85");
        assertThat(outcome.difference().amount()).isEqualByComparingTo("0.00");
        assertThat(outcome.reconciled()).isTrue();
    }

    @Test
    void should_evaluate_withGrossItemsThatEqualTotal_returnCompleteWithoutAddingTax() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("11.90", EUR), LineItemBasis.GROSS,
                List.of(vat("1.90")), List.of(item("Bundle", "11.90")), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(outcome.actual().amount()).isEqualByComparingTo("11.90");
    }

    @Test
    void should_evaluate_withNoItems_returnNeedsReviewAndNoDifference() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("24.00", EUR), LineItemBasis.UNKNOWN,
                List.of(vat("3.83")), List.of(), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(outcome.actual()).isNull();
        assertThat(outcome.difference()).isNull();
    }

    @Test
    void should_evaluate_withItemsThatDoNotSumToTotal_returnNeedsReviewWithGap() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("18.50", EUR), LineItemBasis.NET,
                List.of(vat("1.90")), List.of(item("Water", "4.00"), item("Snacks", "6.00")), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
        assertThat(outcome.expected().amount()).isEqualByComparingTo("18.50");
        assertThat(outcome.actual().amount()).isEqualByComparingTo("11.90");
        assertThat(outcome.difference().amount()).isEqualByComparingTo("6.60");
    }

    @Test
    void should_evaluate_withUnusableHeader_returnFailed() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("18.50", EUR), LineItemBasis.NET,
                List.of(), List.of(item("Water", "4.00")), false));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.FAILED);
    }

    @Test
    void should_evaluate_withNullGrandTotal_returnFailed() {
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(null, LineItemBasis.NET, List.of(), List.of(), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.FAILED);
    }

    @Test
    void should_evaluate_withDifferenceWithinTolerance_returnComplete() {
        // 0.01 rounding drift must not flag a transaction for review.
        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("11.91", EUR), LineItemBasis.NET,
                List.of(vat("1.90")), List.of(item("Water", "4.00"), item("Snacks", "6.00")), true));

        assertThat(outcome.status()).isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void should_evaluate_withMismatch_reportGapWithoutProducingABalancingItem() {
        final List<LineItem> items = List.of(item("Water", "4.00"), item("Snacks", "6.00"));

        final ReconciliationOutcome outcome = service.evaluate(new ReconciliationRequest(Money.of("18.50", EUR), LineItemBasis.NET,
                List.of(vat("1.90")), items, true));

        // The gap is reported as data, never closed: the caller's list is untouched and the total is unchanged.
        assertThat(outcome.difference().amount()).isEqualByComparingTo("6.60");
        assertThat(items).hasSize(2);
        assertThat(outcome.expected().amount()).isEqualByComparingTo("18.50");
    }

    @Test
    void should_forTransaction_withUnusableHeader_carryHeaderUsableFalseFromTheTransaction() {
        // Regression: callers used to hardcode headerUsable=true, so a FAILED transaction could be
        // reconciled to COMPLETE. The flag must come from the transaction, not the caller.
        final Transaction transaction = Transaction.create("receipt-1");
        transaction.applyExtraction(new ExtractionResult(null, null, EUR, Money.of("4.17", EUR),
                null, LineItemBasis.NET, List.of(vat("0.67")), List.of(item("Espresso", "3.50"))),
                "raw text", "stub");

        final ReconciliationRequest request = ReconciliationRequest.forTransaction(transaction,
                List.of(item("Espresso", "3.50")));

        assertThat(transaction.hasUsableHeader()).isFalse();
        assertThat(request.headerUsable()).isFalse();
        assertThat(service.evaluate(request).status()).isEqualTo(ItemizeStatus.FAILED);
    }

    @Test
    void should_forTransaction_withCompleteHeader_carryHeaderUsableTrue() {
        final Transaction transaction = Transaction.create("receipt-2");
        transaction.applyExtraction(new ExtractionResult("Cafe Mitte", LocalDate.of(2026, 3, 12), EUR,
                Money.of("17.85", EUR), Money.of("15.00", EUR), LineItemBasis.NET, List.of(vat("2.85")),
                List.of(item("Espresso", "3.50"), item("Sandwich", "8.90"), item("Mineral water", "2.60"))),
                "raw text", "stub");

        final ReconciliationRequest request = ReconciliationRequest.forTransaction(transaction,
                transaction.getLineItems());

        assertThat(request.headerUsable()).isTrue();
        assertThat(service.evaluate(request).status()).isEqualTo(ItemizeStatus.COMPLETE);
    }
}
