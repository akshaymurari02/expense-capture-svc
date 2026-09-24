package com.harness.expensecapture.service;

import com.harness.expensecapture.extraction.ReceiptTextParser;
import com.harness.expensecapture.extraction.ReconciliationOutcome;
import com.harness.expensecapture.extraction.ReconciliationRequest;
import com.harness.expensecapture.extraction.ReconciliationService;
import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.repository.TransactionRepository;
import com.harness.expensecapture.util.LogConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Re-runs auto-itemize for an existing transaction from its <strong>stored</strong> OCR text.
 *
 * <p>Line items are replaced; the header, taxes and transaction id are untouched, and no second transaction
 * is created. The OCR provider is deliberately not called again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemizeService {

    private final TransactionLookup transactionLookup;
    private final ReceiptTextParser parser;
    private final ReconciliationService reconciliationService;
    private final TransactionRepository transactionRepository;

    public Transaction reitemize(final String transactionId) {
        final Transaction transaction = transactionLookup.requireWithStoredOcr(transactionId);

        final List<LineItem> items = parser.parseLineItems(transaction.getRawOcrText());
        transaction.replaceLineItems(items);

        final ReconciliationOutcome outcome = reconciliationService.evaluate(
                ReconciliationRequest.forTransaction(transaction, items));
        transaction.applyReconciliation(outcome.status(), outcome.difference());

        final Transaction saved = transactionRepository.save(transaction);
        log.info("{} Event : AUTO_ITEMIZE SUCCESS items={} status={}{}", LogConstants.SVC, items.size(),
                saved.getItemizeStatus(), LogConstants.id(saved.getId()));
        return saved;
    }
}
