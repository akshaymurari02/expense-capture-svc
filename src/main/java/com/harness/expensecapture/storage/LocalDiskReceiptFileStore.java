package com.harness.expensecapture.storage;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.UnprocessableException;
import com.harness.expensecapture.model.domain.StoredFilename;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/** Stores receipt files on local disk under the configured upload directory. */
@Component
public class LocalDiskReceiptFileStore implements ReceiptFileStore {

    private final Path uploadDirectory;

    public LocalDiskReceiptFileStore(final Path uploadDirectory) {
        this.uploadDirectory = uploadDirectory;
    }

    @Override
    public Path store(final String receiptId, final StoredFilename filename, final InputStream content) {
        final Path target = uploadDirectory.resolve(receiptId + "_" + filename.value());
        // Defence in depth: StoredFilename already strips traversal, but assert the outcome rather than
        // trusting an upstream invariant that a future caller could bypass.
        final Path resolved = target.toAbsolutePath().normalize();
        if (!resolved.startsWith(uploadDirectory)) {
            throw new UnprocessableException(ErrorCodes.STORAGE_FAILED,
                    "Refusing to store outside the upload directory");
        }
        try {
            Files.copy(content, resolved, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new UnprocessableException(ErrorCodes.STORAGE_FAILED,
                    "Failed to store uploaded receipt: " + e.getMessage(), e);
        }
        return resolved;
    }
}
