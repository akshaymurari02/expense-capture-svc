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
import com.harness.expensecapture.model.domain.OverrideKind;
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
        final int previousCount = transaction.getLineItems().size();

        // Ask the aggregate what it owns rather than reading its items and deciding here.
        final List<String> suppliedIds = request.items().stream()
                .map(LineItemInput::itemId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (!transaction.ownsAllLineItems(suppliedIds)) {
            throw new ValidationException(ErrorCodes.INVALID_ITEM_PAYLOAD,
                    "Request references an item_id that does not belong to this transaction");
        }

        final List<LineItem> items = request.items().stream()
                .map(input -> toLineItem(input, transaction.getCurrency()))
                .toList();

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
        final OverrideKind kind = OverrideKind.of(previousCount, items.size());
        log.info("{} Event : ITEM_OVERRIDE SUCCESS kind={} before={} after={}{}", LogConstants.SVC, kind,
                previousCount, items.size(), LogConstants.id(saved.getId()));
        return saved;
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
