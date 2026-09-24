package com.harness.expensecapture.model.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Aggregate root for an expense transaction derived from one receipt.
 *
 * <p>{@link Tax} and {@link LineItem} are members of this aggregate: they have no lifecycle outside it and
 * are only ever persisted through this root. {@link Receipt} is a separate aggregate, referenced by id.
 *
 * <p><strong>Reconciliation is a soft invariant here.</strong> A transaction whose items do not sum to the
 * grand total is a legal, persisted state carrying {@link ItemizeStatus#NEEDS_REVIEW}; the machine's guess
 * is kept as evidence for a human to correct. The hard invariant lives on the user-override path, where a
 * non-reconciling edit is rejected before any write. The grand total is taken from the receipt and is never
 * recomputed from the line items.
 */
@Getter
public final class Transaction {

    private final String id;
    private final String receiptId;
    private final Instant createdAt;

    private String merchant;
    private LocalDate transactionDate;
    private String currency;
    private Money grandTotal;
    private Money printedSubtotal;
    private LineItemBasis lineItemBasis = LineItemBasis.UNKNOWN;
    private ItemizeStatus itemizeStatus = ItemizeStatus.FAILED;
    private Money reconciliationGap;
    private String rawOcrText;
    private String ocrProvider;
    private Instant updatedAt;

    private final List<Tax> taxes = new ArrayList<>();
    private final List<LineItem> lineItems = new ArrayList<>();

    private Transaction(final String id, final String receiptId) {
        this.id = id;
        this.receiptId = receiptId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Transaction create(final String receiptId) {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        return new Transaction(UUID.randomUUID().toString(), receiptId);
    }

    /**
     * Applies one extraction to this transaction as a single operation: header, OCR provenance, taxes, line
     * items and basis together.
     *
     * <p>Callers previously invoked five mutators in sequence, which leaked the required ordering into the
     * service and left the aggregate observably half-updated in between. Reconciliation is deliberately
     * *not* done here: the status depends on a configured tolerance that is policy, not domain state, so it
     * arrives separately via {@link #applyReconciliation}.
     */
    public void applyExtraction(final ExtractedFields extracted, final String rawOcrText,
            final String ocrProvider) {
        Objects.requireNonNull(extracted, "extracted must not be null");
        this.merchant = extracted.merchant();
        this.transactionDate = extracted.transactionDate();
        this.currency = extracted.currency();
        this.grandTotal = extracted.grandTotal();
        this.printedSubtotal = extracted.printedSubtotal();
        this.lineItemBasis = extracted.lineItemBasis() == null
                ? LineItemBasis.UNKNOWN
                : extracted.lineItemBasis();
        this.rawOcrText = rawOcrText;
        this.ocrProvider = ocrProvider;
        replaceCollection(taxes, extracted.taxes());
        replaceCollection(lineItems, extracted.lineItems());
        touch();
    }

    /**
     * Replaces the line item collection. Used by re-itemize and user override alike; header fields and taxes
     * are deliberately untouched.
     */
    public void replaceLineItems(final List<LineItem> replacement) {
        replaceCollection(lineItems, replacement);
        touch();
    }

    /**
     * Applies a user override as one state transition: the new items and the resulting status together.
     *
     * <p>Callers previously invoked {@code replaceLineItems} then {@code applyReconciliation}, two mutations
     * that must both happen or neither, sequenced by the service. As one method the aggregate is never
     * observable with new items but a stale status.
     *
     * <p>Takes {@code status} and {@code gap} rather than the extraction layer's {@code ReconciliationOutcome}:
     * that type already depends on this package, so accepting it here would make the two cyclic. Reconciliation
     * arithmetic also needs a configured tolerance, which is policy rather than domain state, so it stays
     * outside the aggregate.
     *
     * @throws IllegalArgumentException if two replacement items claim the same id
     */
    public void overrideLineItems(final List<LineItem> replacement, final ItemizeStatus status, final Money gap) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Objects.requireNonNull(status, "status must not be null");
        rejectDuplicateIds(replacement);
        // Every guard has passed, so from here the transition cannot fail part-way through.
        replaceCollection(lineItems, replacement);
        this.itemizeStatus = status;
        this.reconciliationGap = gap;
        touch();
    }

    /**
     * Whether every supplied id belongs to this transaction. Only the aggregate knows what it owns, so the
     * question is answered here rather than by a caller reading {@link #getLineItems()} and deciding.
     */
    public boolean ownsAllLineItems(final Collection<String> itemIds) {
        final Set<String> owned = lineItems.stream().map(LineItem::id).collect(Collectors.toSet());
        return owned.containsAll(itemIds);
    }

    /** Two rows claiming one id would silently collapse a split into an edit. */
    private static void rejectDuplicateIds(final List<LineItem> replacement) {
        final Set<String> seen = new HashSet<>();
        for (final LineItem item : replacement) {
            if (!seen.add(item.id())) {
                throw new IllegalArgumentException("duplicate line item id in replacement: " + item.id());
            }
        }
    }

    public void applyReconciliation(final ItemizeStatus status, final Money gap) {
        this.itemizeStatus = Objects.requireNonNull(status, "status must not be null");
        this.reconciliationGap = gap;
        touch();
    }

    public List<Tax> getTaxes() {
        return Collections.unmodifiableList(taxes);
    }

    public List<LineItem> getLineItems() {
        return Collections.unmodifiableList(lineItems);
    }

    public boolean hasStoredOcrText() {
        return rawOcrText != null && !rawOcrText.isBlank();
    }

    /**
     * Whether the header carries enough to reconcile against: merchant, date and a positive grand total.
     *
     * <p>Lives on the aggregate because it is a fact about this transaction, not a caller's opinion. Callers
     * previously passed a {@code headerUsable} flag to the reconciliation service and both hardcoded
     * {@code true}, which let a {@code FAILED} transaction be reported {@code COMPLETE}.
     */
    public boolean hasUsableHeader() {
        return ExtractedFields.isUsableHeader(merchant, transactionDate, grandTotal);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    private static <T> void replaceCollection(final List<T> target, final List<T> replacement) {
        target.clear();
        if (replacement != null) {
            target.addAll(replacement);
        }
    }
}
