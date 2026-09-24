package com.harness.expensecapture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import com.harness.expensecapture.extraction.ReconciliationService;
import com.harness.expensecapture.model.domain.ExtractedFields;
import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.model.dto.LineItemInput;
import com.harness.expensecapture.model.dto.UpdateItemsRequest;
import com.harness.expensecapture.repository.InMemoryTransactionRepository;
import com.harness.expensecapture.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Domain invariants raised while building the replacement items must surface as 400, not 500.
 *
 * <p>Driven at the service rather than over HTTP because the parser defaults a missing currency to {@code XXX},
 * so the API cannot currently produce a transaction with a blank currency. That default is the only reason the
 * path is unreachable — it is a distant assumption, not a guarantee, so the translation is pinned here.
 */
@DisplayName("Item override failure translation")
class ItemOverrideServiceTest {

    private static final String EUR = "EUR";

    private TransactionRepository transactionRepository;
    private ItemOverrideService service;

    @BeforeEach
    void setUp() {
        final AppProperties properties = new AppProperties();
        properties.getReconciliation().setTolerance(new BigDecimal("0.01"));
        transactionRepository = new InMemoryTransactionRepository();
        service = new ItemOverrideService(
                new TransactionLookup(transactionRepository),
                new ReconciliationService(properties),
                transactionRepository);
    }

    private Transaction storedTransaction(final String currency) {
        final Transaction transaction = Transaction.create("receipt-1");
        transaction.applyExtraction(new Extraction(currency), "raw ocr", "stub");
        return transactionRepository.save(transaction);
    }

    @Test
    void should_replaceItems_withBlankCurrencyOnTransaction_returnValidationErrorNotServerError() {
        final Transaction stored = storedTransaction("  ");
        final UpdateItemsRequest request = new UpdateItemsRequest(
                List.of(new LineItemInput(null, "Widget", new BigDecimal("15.00"), null, null)));

        // Money rejects a blank currency with IllegalArgumentException. Unwrapped, that is a 500.
        assertThatThrownBy(() -> service.replaceItems(stored.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.INVALID_ITEM_PAYLOAD);
    }

    @Test
    void should_replaceItems_withNullAmount_returnValidationErrorNotServerError() {
        final Transaction stored = storedTransaction(EUR);
        final UpdateItemsRequest request = new UpdateItemsRequest(
                List.of(new LineItemInput(null, "Widget", null, null, null)));

        assertThatThrownBy(() -> service.replaceItems(stored.getId(), request))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.INVALID_ITEM_PAYLOAD);
    }

    @Test
    void should_replaceItems_whenItemBuildingFails_leaveTheStoredItemsUntouched() {
        final Transaction stored = storedTransaction("  ");
        final List<LineItem> before = List.copyOf(stored.getLineItems());

        assertThatThrownBy(() -> service.replaceItems(stored.getId(), new UpdateItemsRequest(
                List.of(new LineItemInput(null, "Widget", new BigDecimal("15.00"), null, null)))))
                .isInstanceOf(ValidationException.class);

        // The failure happens before the aggregate is touched, so nothing may have changed.
        assertThat(transactionRepository.findById(stored.getId()).orElseThrow().getLineItems())
                .isEqualTo(before);
    }

    @Test
    void should_replaceItems_withValidPayload_succeedAndKeepGrandTotal() {
        final Transaction stored = storedTransaction(EUR);

        final Transaction result = service.replaceItems(stored.getId(), new UpdateItemsRequest(
                List.of(new LineItemInput(null, "Combined", new BigDecimal("15.00"), null, null))));

        assertThat(result.getItemizeStatus()).isEqualTo(ItemizeStatus.COMPLETE);
        assertThat(result.getLineItems()).hasSize(1);
        assertThat(result.getGrandTotal().amount()).isEqualByComparingTo("17.85");
    }

    /** 15.00 of net items plus 2.85 VAT against a 17.85 gross total. */
    private record Extraction(String currencyCode) implements ExtractedFields {
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
            return currencyCode;
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
            return List.of(LineItem.create("Espresso", Money.of("15.00", EUR)));
        }
    }
}
