package com.harness.expensecapture.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full transaction view: header, its own tax records, its line items, and the itemize status.
 *
 * <p>Raw OCR text is deliberately <strong>not</strong> exposed. The brief requires it to be stored and used as
 * the source for re-itemize, not returned: it can run to kilobytes and may carry names, addresses or partial
 * card numbers, so the narrower surface is the safer default. {@code ocr.text_length} evidences that it was
 * persisted, and {@code POST /transactions/{id}/itemize} evidences that it is usable.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TransactionResponse(
        String transactionId,
        String receiptId,
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        BigDecimal printedSubtotal,
        LineItemBasis lineItemBasis,
        List<TaxDto> taxes,
        List<LineItemDto> lineItems,
        ItemizeStatus itemizeStatus,
        ReconciliationDto reconciliation,
        OcrDto ocr,
        Instant createdAt,
        Instant updatedAt) {

    /** Reconciliation evidence. {@code difference} is reported, never silently corrected. */
    public record ReconciliationDto(BigDecimal expected, BigDecimal actual, BigDecimal difference) {
    }

    public record OcrDto(String provider, int textLength) {
    }

    public static TransactionResponse from(final Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReceiptId(),
                transaction.getMerchant(),
                transaction.getTransactionDate(),
                transaction.getCurrency(),
                amountOf(transaction.getGrandTotal()),
                amountOf(transaction.getPrintedSubtotal()),
                transaction.getLineItemBasis(),
                transaction.getTaxes().stream().map(TaxDto::from).toList(),
                transaction.getLineItems().stream().map(LineItemDto::from).toList(),
                transaction.getItemizeStatus(),
                reconciliationOf(transaction),
                new OcrDto(transaction.getOcrProvider(),
                        transaction.getRawOcrText() == null ? 0 : transaction.getRawOcrText().length()),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }

    private static ReconciliationDto reconciliationOf(final Transaction transaction) {
        final BigDecimal expected = amountOf(transaction.getGrandTotal());
        final BigDecimal difference = amountOf(transaction.getReconciliationGap());
        final BigDecimal actual = expected == null || difference == null ? null : expected.subtract(difference);
        return new ReconciliationDto(expected, actual, difference);
    }

    private static BigDecimal amountOf(final Money money) {
        return money == null ? null : money.amount();
    }
}
