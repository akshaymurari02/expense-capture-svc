package com.harness.expensecapture.service;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ReconciliationConflictException;
import com.harness.expensecapture.exception.ValidationException;
import com.harness.expensecapture.extraction.ReconciliationOutcome;
import com.harness.expensecapture.extraction.ReconciliationRequest;
import com.harness.expensecapture.extraction.ReconciliationService;
import com.harness.expensecapture.model.domain.ItemizeStatus;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.OverrideSummary;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.model.dto.LineItemInput;
import com.harness.expensecapture.model.dto.MismatchDetail;
import com.harness.expensecapture.model.dto.UpdateItemsRequest;
import com.harness.expensecapture.repository.TransactionRepository;
import com.harness.expensecapture.util.LogConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Applies a user's edit / merge / split of line items.
 *
 * <p>All three verbs are one operation: the client sends the complete replacement list, so a merge sends fewer
 * items, a split more, an edit the same number. Supplying an existing {@code item_id} preserves that item's
 * identity, which is what makes an edit a real edit rather than a delete-and-recreate; omitting it mints a new
 * id, which is correct for the products of a merge or split since they are not any of the originals.
 *
 * <p>This is the hard half of the reconciliation invariant. A machine-produced mismatch is persisted as
 * {@code NEEDS_REVIEW} because the guess is evidence worth keeping; a human-supplied mismatch is an
 * assertion, so if it is arithmetically false the request is rejected with 409 and <strong>nothing is
 * written</strong>. Totals are never adjusted to make the sums agree.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemOverrideService {

    private final TransactionLookup transactionLookup;
    private final ReconciliationService reconciliationService;
    private final TransactionRepository transactionRepository;

    public Transaction replaceItems(final String transactionId, final UpdateItemsRequest request) {
        final Transaction transaction = transactionLookup.requireById(transactionId);
        // Copy before any mutation: overrideLineItems clears the aggregate's list in place, and findById
        // hands back the stored instance itself, so a live view would be emptied before it could be compared.
        final List<LineItem> previousItems = List.copyOf(transaction.getLineItems());

        // Ask the aggregate what it owns rather than reading its items and deciding here.
        final List<String> suppliedIds = request.items().stream()
                .map(LineItemInput::itemId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!transaction.ownsAllLineItems(suppliedIds)) {
            throw new ValidationException(ErrorCodes.INVALID_ITEM_PAYLOAD,
                    "Request references an item_id that does not belong to this transaction");
        }

        // Read the currency once rather than per item: it is mutable aggregate state.
        final String currency = transaction.getCurrency();
        final List<LineItem> items = toLineItems(request, currency);

        final ReconciliationOutcome outcome = reconciliationService.evaluate(
                ReconciliationRequest.forTransaction(transaction, items));

        if (!outcome.reconciled()) {
            log.warn("{} Event : ITEM_OVERRIDE FAILURE reason=RECONCILIATION_MISMATCH{}",
                    LogConstants.SVC, LogConstants.id(transactionId));
            throw new ReconciliationConflictException(
                    "Line items do not reconcile with the transaction total", toMismatch(transaction, outcome));
        }

        applyOverride(transaction, items, outcome);
        final Transaction saved = transactionRepository.save(transaction);
        final OverrideSummary summary = OverrideSummary.of(previousItems, items);
        log.info("{} Event : ITEM_OVERRIDE SUCCESS before={} after={} kept={} created={} removed={}{}",
                LogConstants.SVC, summary.before(), summary.after(), summary.kept(), summary.created(),
                summary.removed(), LogConstants.id(saved.getId()));
        return saved;
    }

    /**
     * Builds the candidate items into a local list; the aggregate is deliberately untouched until the
     * reconciliation gate has passed.
     *
     * <p>{@code Money} and {@code LineItem} enforce their own invariants with {@link IllegalArgumentException}
     * — a blank currency, a null amount. Those are caller-visible faults, so they are translated to 400 here.
     * Without this the catch-all would report them as 500, telling the client to retry a request that can
     * never succeed.
     */
    private List<LineItem> toLineItems(final UpdateItemsRequest request, final String currency) {
        try {
            return request.items().stream()
                    .map(input -> toLineItem(input, currency))
                    .toList();
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new ValidationException(ErrorCodes.INVALID_ITEM_PAYLOAD,
                    "Line items could not be built: " + e.getMessage());
        }
    }

    /**
     * Tell, don't ask: one call mutates the aggregate, so it is never observable with new items but a stale
     * status. The aggregate rejects a malformed replacement with {@link IllegalArgumentException}; translate it
     * at this boundary so the client sees a 400 rather than a 500.
     */
    private void applyOverride(final Transaction transaction, final List<LineItem> items,
            final ReconciliationOutcome outcome) {
        try {
            transaction.overrideLineItems(items, ItemizeStatus.COMPLETE, outcome.difference());
        } catch (final IllegalArgumentException e) {
            throw new ValidationException(ErrorCodes.INVALID_ITEM_PAYLOAD, e.getMessage());
        }
    }

    /**
     * Keeps the caller's {@code item_id} so an edit preserves identity, and mints one otherwise — correct for
     * the products of a merge or split, which are not any of the originals. Ownership of a supplied id is
     * checked by the aggregate before this runs.
     */
    private LineItem toLineItem(final LineItemInput input, final String currency) {
        final String suppliedId = input.itemId() == null || input.itemId().isBlank() ? null : input.itemId();
        return new LineItem(
                suppliedId == null ? LineItem.newId() : suppliedId,
                input.description().trim(),
                Money.of(input.amount(), currency),
                input.taxAmount() == null ? null : Money.of(input.taxAmount(), currency),
                input.quantity());
    }

    private MismatchDetail toMismatch(final Transaction transaction, final ReconciliationOutcome outcome) {
        return new MismatchDetail(
                outcome.expected() == null ? null : outcome.expected().amount(),
                outcome.actual() == null ? null : outcome.actual().amount(),
                outcome.difference() == null ? null : outcome.difference().amount(),
                transaction.getLineItemBasis(),
                transaction.getCurrency());
    }
}
