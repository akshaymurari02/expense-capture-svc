package com.harness.expensecapture.storage;

import com.harness.expensecapture.model.domain.StoredFilename;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Where uploaded receipt bytes go. Keeps filesystem concerns out of the service layer, so swapping local disk
 * for object storage is one new implementation rather than an edit to upload orchestration.
 */
public interface ReceiptFileStore {

    /**
     * Stores the content and returns its final location.
     *
     * @param receiptId used to make the stored name unique; two uploads of {@code receipt.png} must not collide
     * @param filename  already sanitised, so it is safe to use as a path segment
     */
    Path store(String receiptId, StoredFilename filename, InputStream content);
}
