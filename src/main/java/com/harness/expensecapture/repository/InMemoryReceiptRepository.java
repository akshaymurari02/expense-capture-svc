package com.harness.expensecapture.repository;

import com.harness.expensecapture.model.domain.Receipt;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory {@link ReceiptRepository}. Sufficient per the brief; swappable for JDBC without API change. */
@Repository
public class InMemoryReceiptRepository implements ReceiptRepository {

    private final Map<String, Receipt> store = new ConcurrentHashMap<>();

    @Override
    public Receipt save(final Receipt receipt) {
        store.put(receipt.id(), receipt);
        return receipt;
    }

    @Override
    public Optional<Receipt> findById(final String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(store.get(id));
    }

    @Override
    public long count() {
        return store.size();
    }
}
