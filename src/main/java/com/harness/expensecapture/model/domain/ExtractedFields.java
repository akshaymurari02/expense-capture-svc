package com.harness.expensecapture.model.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * The fields an extraction must supply to populate a {@link Transaction}.
 *
 * <p>Declared here, in the domain, rather than taking the extraction layer's own result type as a parameter:
 * that type already depends on this package, so depending back on it would make the two packages cyclic.
 * The domain states what it needs and the extraction layer conforms — the dependency arrow points inward.
 */
public interface ExtractedFields {

    /**
     * The single definition of a usable header: merchant, date and a positive grand total all present.
     *
     * <p>Static so that {@code Transaction} and the extraction result share one rule. Duplicating it would let
     * the two drift, and the two must agree — {@code process} decides {@code FAILED} from the extraction while
     * re-itemize and override decide it from the stored transaction.
     */
    static boolean isUsableHeader(final String merchant, final LocalDate transactionDate, final Money grandTotal) {
        return merchant != null && !merchant.isBlank()
                && transactionDate != null
                && grandTotal != null
                && grandTotal.isPositive();
    }

    String merchant();

    LocalDate transactionDate();

    String currency();

    Money grandTotal();

    Money printedSubtotal();

    LineItemBasis lineItemBasis();

    List<Tax> taxes();

    List<LineItem> lineItems();

    /**
     * A header is usable only when merchant, date and a positive grand total are all present. Without those the
     * extraction is {@code FAILED} rather than merely in need of review.
     */
    default boolean headerUsable() {
        return isUsableHeader(merchant(), transactionDate(), grandTotal());
    }
}
