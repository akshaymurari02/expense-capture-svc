package com.harness.expensecapture.extraction;

import com.harness.expensecapture.model.domain.ExtractedFields;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import java.time.LocalDate;
import java.util.List;

/**
 * Structured fields extracted from raw receipt text.
 *
 * @param printedSubtotal the net subtotal as printed, or {@code null} when the receipt does not show one
 * @param lineItemBasis   whether {@code lineItems} amounts exclude or include tax
 */
public record ExtractionResult(
        String merchant,
        LocalDate transactionDate,
        String currency,
        Money grandTotal,
        Money printedSubtotal,
        LineItemBasis lineItemBasis,
        List<Tax> taxes,
        List<LineItem> lineItems) implements ExtractedFields {
}
