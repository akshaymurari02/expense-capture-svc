package com.harness.expensecapture.service;

import com.harness.expensecapture.extraction.ExtractionResult;
import com.harness.expensecapture.extraction.ReceiptTextParser;
import com.harness.expensecapture.extraction.ReconciliationOutcome;
import com.harness.expensecapture.extraction.ReconciliationRequest;
import com.harness.expensecapture.extraction.ReconciliationService;
import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.ocr.OcrProvider;
import com.harness.expensecapture.repository.TransactionRepository;
import com.harness.expensecapture.util.LogConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Runs OCR and extraction for a receipt and produces exactly one transaction from it.
 *
 * <p>Idempotent: processing the same receipt again updates the existing transaction in place, keeping its id
 * stable, rather than creating a second one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptProcessingService {

    private final ReceiptService receiptService;
    private final OcrProvider ocrProvider;
    private final ReceiptTextParser parser;
    private final ReconciliationService reconciliationService;
    private final TransactionRepository transactionRepository;

    public Transaction process(final String receiptId) {
        final long startedAt = System.currentTimeMillis();
        final Receipt receipt = receiptService.requireById(receiptId);

        final String rawText = ocrProvider.extractText(receipt);
        final ExtractionResult extraction = parser.parse(rawText);

        final Transaction transaction = transactionRepository.findOrCreateByReceiptId(receiptId,
                () -> Transaction.create(receiptId));

        transaction.applyExtraction(extraction, rawText, ocrProvider.providerName());

        log.info("{} Parsed {} line items, {} taxes, basis={}{}", LogConstants.SVC,
                extraction.lineItems().size(), extraction.taxes().size(), extraction.lineItemBasis(),
                LogConstants.id(receiptId, transaction.getId()));

        final ReconciliationOutcome outcome = reconciliationService.evaluate(
                ReconciliationRequest.forExtraction(extraction));
        transaction.applyReconciliation(outcome.status(), outcome.difference());

        if (!outcome.reconciled()) {
            log.warn("{} Itemization needs review. expected={} actual={} difference={}{}",
                    LogConstants.SVC, outcome.expected(), outcome.actual(), outcome.difference(),
                    LogConstants.id(receiptId, transaction.getId()));
        }

        final Transaction saved = transactionRepository.save(transaction);
        log.info("{} Event : RECEIPT_PROCESSING SUCCESS status={} duration={}ms{}", LogConstants.SVC,
                saved.getItemizeStatus(), System.currentTimeMillis() - startedAt,
                LogConstants.id(receiptId, saved.getId()));
        return saved;
    }
}
