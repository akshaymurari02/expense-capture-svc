package com.harness.expensecapture.repository;

import com.harness.expensecapture.model.domain.Transaction;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Persistence port for the {@link Transaction} aggregate. There is deliberately no repository for
 * {@code Tax} or {@code LineItem}: they are aggregate members, saved only through their root.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findById(String id);

    /** Supports the one-transaction-per-receipt rule and makes {@code process} idempotent. */
    Optional<Transaction> findByReceiptId(String receiptId);

    /**
     * Returns the receipt's existing transaction, or stores and returns the one built by {@code factory}.
     *
     * <p>This exists because {@code findByReceiptId(...).orElseGet(Transaction::create)} in a caller is a
     * check-then-act race: two concurrent {@code process} calls for one receipt both find nothing and both
     * create, breaking the one-transaction-per-receipt rule. Making the invariant the repository's own
     * responsibility is the only place it can be enforced atomically.
     *
     * @param factory invoked at most once per receipt; must not return {@code null}
     */
    Transaction findOrCreateByReceiptId(String receiptId, Supplier<Transaction> factory);

    long count();
}
