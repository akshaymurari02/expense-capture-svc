package com.harness.expensecapture.extraction;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Decides whether line items add up to the receipt total, and what status that implies.
 *
 * <p>This class reports the gap; it never closes it. No code path here or anywhere else constructs a
 * {@link LineItem} from a residual difference, and the grand total is never adjusted to match the items.
 *
 * <p>Deliberately a concrete class behind no interface: it is a pure function
 * {@code ReconciliationRequest -> ReconciliationOutcome} whose only collaborator is configuration. Its sole
 * dependency is the tolerance, which is policy rather than a replaceable service, so there is no seam worth
 * abstracting — its tests need no mocks.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final AppProperties properties;

    public ReconciliationOutcome evaluate(final ReconciliationRequest request) {
        final Money grandTotal = request.grandTotal();
        if (!request.headerUsable() || grandTotal == null) {
            return new ReconciliationOutcome(ItemizeStatus.FAILED, grandTotal, null, null);
        }
        final List<LineItem> items = request.items();
        if (items == null || items.isEmpty()) {
            // No items to compare: keep the transaction, flag it for a human. Never synthesise an item.
            return new ReconciliationOutcome(ItemizeStatus.NEEDS_REVIEW, grandTotal, null, null);
        }

        final String currency = grandTotal.currency();
        final Money itemSum = sumItems(items, currency);
        final Money actual = request.basis() == LineItemBasis.GROSS
                ? itemSum
                : itemSum.add(sumTaxes(request.taxes(), currency));
        final Money difference = grandTotal.subtract(actual);

        final boolean reconciles = grandTotal.equalsWithin(actual, properties.getReconciliation().getTolerance());
        return new ReconciliationOutcome(
                reconciles ? ItemizeStatus.COMPLETE : ItemizeStatus.NEEDS_REVIEW,
                grandTotal,
                actual,
                difference);
    }

    private Money sumItems(final List<LineItem> items, final String currency) {
        Money sum = Money.zero(currency);
        for (final LineItem item : items) {
            sum = sum.add(Money.of(item.amount().amount(), currency));
        }
        return sum;
    }

    private Money sumTaxes(final List<Tax> taxes, final String currency) {
        Money sum = Money.zero(currency);
        if (taxes == null) {
            return sum;
        }
        for (final Tax tax : taxes) {
            sum = sum.add(Money.of(tax.amount().amount(), currency));
        }
        return sum;
    }
}
