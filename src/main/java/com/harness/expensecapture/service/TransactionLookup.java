package com.harness.expensecapture.service;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.NotFoundException;
import com.harness.expensecapture.exception.UnprocessableException;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Shared transaction lookup so every service reports a missing transaction identically. */
@Service
@RequiredArgsConstructor
public class TransactionLookup {

    private final TransactionRepository transactionRepository;

    public Transaction requireById(final String transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException(ErrorCodes.TRANSACTION_NOT_FOUND,
                        "Transaction not found: " + transactionId));
    }

    public Transaction requireWithStoredOcr(final String transactionId) {
        final Transaction transaction = requireById(transactionId);
        if (!transaction.hasStoredOcrText()) {
            throw new UnprocessableException(ErrorCodes.NO_STORED_OCR,
                    "No stored OCR text for transaction " + transactionId + "; run process first");
        }
        return transaction;
    }
}
