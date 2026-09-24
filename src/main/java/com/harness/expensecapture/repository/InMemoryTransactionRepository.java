package com.harness.expensecapture.repository;

import com.harness.expensecapture.model.domain.Transaction;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link TransactionRepository}. A second index on {@code receiptId} enforces the
 * one-transaction-per-receipt rule and gives {@code process} its idempotency.
 */
@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final Map<String, Transaction> byId = new ConcurrentHashMap<>();
    private final Map<String, String> transactionIdByReceiptId = new ConcurrentHashMap<>();

    @Override
    public Transaction save(final Transaction transaction) {
        // Entity first, then the index: a reader that sees the index must always be able to resolve it.
        byId.put(transaction.getId(), transaction);
        transactionIdByReceiptId.put(transaction.getReceiptId(), transaction.getId());
        return transaction;
    }

    @Override
    public Transaction findOrCreateByReceiptId(final String receiptId, final Supplier<Transaction> factory) {
        Objects.requireNonNull(receiptId, "receiptId must not be null");
        Objects.requireNonNull(factory, "factory must not be null");
        // computeIfAbsent's mapping function runs at most once per key under the bin lock, so concurrent
        // callers for the same receipt cannot both create. A plain get/put pair here would not be atomic.
        final String transactionId = transactionIdByReceiptId.computeIfAbsent(receiptId, key -> {
            final Transaction created = Objects.requireNonNull(factory.get(), "factory returned null");
            byId.put(created.getId(), created);
            return created.getId();
        });
        return byId.get(transactionId);
    }

    @Override
    public Optional<Transaction> findById(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Transaction> findByReceiptId(final String receiptId) {
        if (receiptId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transactionIdByReceiptId.get(receiptId)).map(byId::get);
    }

    @Override
    public long count() {
        return byId.size();
    }
}
