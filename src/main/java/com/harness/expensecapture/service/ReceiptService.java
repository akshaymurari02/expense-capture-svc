package com.harness.expensecapture.service;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.NotFoundException;
import com.harness.expensecapture.exception.UnprocessableException;
import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.model.domain.StoredFilename;
import com.harness.expensecapture.repository.ReceiptRepository;
import com.harness.expensecapture.storage.FileSignature;
import com.harness.expensecapture.storage.ReceiptFileStore;
import com.harness.expensecapture.util.LogConstants;
import com.harness.expensecapture.validation.ReceiptUpload;
import com.harness.expensecapture.validation.UploadValidationChain;
import java.io.IOException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded receipt files.
 *
 * <p>Orchestration only. Validation belongs to {@link UploadValidationChain}, sanitising to
 * {@link StoredFilename} and writing bytes to {@link ReceiptFileStore}, so this class contains no rules and no
 * filesystem logic — which is also why it no longer needs {@code AppProperties}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptFileStore fileStore;
    private final UploadValidationChain validationChain;

    public Receipt upload(final MultipartFile file) {
        final StoredFilename filename;
        final String receiptId = Receipt.newId();
        final Path storedPath;

        try (ReceiptUpload upload = ReceiptUpload.open(file, FileSignature.maxMagicLength())) {
            validationChain.validate(upload);
            filename = StoredFilename.from(upload.clientFilename());
            storedPath = fileStore.store(receiptId, filename, upload.content());
        } catch (final IOException e) {
            throw new UnprocessableException(ErrorCodes.STORAGE_FAILED,
                    "Failed to read uploaded receipt: " + e.getMessage(), e);
        }

        final Receipt stored = receiptRepository.save(Receipt.create(receiptId, filename, storedPath,
                file.getContentType(), file.getSize()));
        log.info("{} Event : RECEIPT_UPLOAD SUCCESS filename={} bytes={}{}", LogConstants.SVC, filename,
                file.getSize(), LogConstants.id(stored.id()));
        return stored;
    }

    public Receipt requireById(final String receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCodes.RECEIPT_NOT_FOUND, "Receipt not found: " + receiptId));
    }
}
