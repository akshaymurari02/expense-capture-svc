package com.harness.expensecapture.extraction;

import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import com.harness.expensecapture.model.domain.Transaction;
import java.util.List;

/**
 * Everything reconciliation needs to judge a set of line items.
 *
 * <p>Replaces a five-positional-parameter call. Four of those arguments came off the same {@code Transaction},
 * and the fifth — {@code headerUsable} — was hardcoded {@code true} by two of the three callers, which let a
 * {@code FAILED} transaction be reported {@code COMPLETE}. A named holder built by a factory method makes that
 * class of mistake unavailable: callers no longer supply the flag, they supply the transaction.
 *
 * @param headerUsable whether the header can be reconciled against at all
 */
public record ReconciliationRequest(
        Money grandTotal,
        LineItemBasis basis,
        List<Tax> taxes,
        List<LineItem> items,
        boolean headerUsable) {

    /** Reconciles the extraction that {@code process} just produced. */
    public static ReconciliationRequest forExtraction(final ExtractionResult extraction) {
        return new ReconciliationRequest(extraction.grandTotal(), extraction.lineItemBasis(),
                extraction.taxes(), extraction.lineItems(), extraction.headerUsable());
    }

    /**
     * Reconciles {@code candidateItems} against a stored transaction, taking {@code headerUsable} from the
     * transaction itself rather than from the caller.
     */
    public static ReconciliationRequest forTransaction(final Transaction transaction,
            final List<LineItem> candidateItems) {
        return new ReconciliationRequest(transaction.getGrandTotal(), transaction.getLineItemBasis(),
                transaction.getTaxes(), candidateItems, transaction.hasUsableHeader());
    }
}
