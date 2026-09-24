package com.harness.expensecapture.repository;

import com.harness.expensecapture.model.domain.Receipt;
import java.util.Optional;

/** Persistence port for the {@link Receipt} aggregate. */
public interface ReceiptRepository {

    Receipt save(Receipt receipt);

    Optional<Receipt> findById(String id);

    long count();
}
