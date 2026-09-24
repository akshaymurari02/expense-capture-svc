package com.harness.expensecapture.validation;

import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.UnprocessableException;
import com.harness.expensecapture.exception.ValidationException;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;

/**
 * One readable view of an upload, shared by every validator and then by the file store.
 *
 * <p>Validators must not each call {@code getInputStream()}: {@link MultipartFile} makes no promise that the
 * stream is re-readable, and re-reading would mean N passes over the payload. So the leading bytes are read
 * once here behind {@code mark}/{@code reset} and cached, leaving the stream positioned at zero for the store.
 * Validators become pure functions over this object and do no I/O of their own.
 *
 * <p>Owns the stream, so callers must use it in a try-with-resources block.
 */
public final class ReceiptUpload implements Closeable {

    private final MultipartFile file;
    private final BufferedInputStream content;
    private final byte[] head;

    private ReceiptUpload(final MultipartFile file, final BufferedInputStream content, final byte[] head) {
        this.file = file;
        this.content = content;
        this.head = head;
    }

    /**
     * Opens the upload and reads its first {@code headLength} bytes.
     *
     * <p>The null/empty check lives here rather than in a validator because it is a precondition for this
     * object existing at all: there is no upload to validate, so there is nothing to hand a validator.
     *
     * @throws ValidationException when no usable file part was supplied
     */
    public static ReceiptUpload open(final MultipartFile file, final int headLength) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCodes.FILE_MISSING, "A non-empty 'file' part is required");
        }
        BufferedInputStream buffered = null;
        try {
            buffered = new BufferedInputStream(file.getInputStream());
            buffered.mark(headLength + 1);
            final byte[] leadingBytes = buffered.readNBytes(headLength);
            buffered.reset();
            return new ReceiptUpload(file, buffered, leadingBytes);
        } catch (final IOException e) {
            closeQuietly(buffered);
            throw new UnprocessableException(ErrorCodes.STORAGE_FAILED,
                    "Failed to read uploaded receipt: " + e.getMessage(), e);
        }
    }

    /** The stream, positioned at byte zero. */
    public InputStream content() {
        return content;
    }

    /** Leading bytes, already read. Never null; shorter than requested for a very small file. */
    public byte[] head() {
        return head.clone();
    }

    public long sizeBytes() {
        return file.getSize();
    }

    /** Exactly what the client sent, for error messages. */
    public String rawContentType() {
        return file.getContentType();
    }

    /** Lower-cased content type without parameters, for comparison against an allowlist. */
    public String normalisedContentType() {
        final String contentType = file.getContentType();
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
    }

    /** Client-supplied and therefore hostile; sanitise before use as a path segment. */
    public String clientFilename() {
        return file.getOriginalFilename();
    }

    @Override
    public void close() throws IOException {
        content.close();
    }

    private static void closeQuietly(final Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (final IOException suppressed) {
            // Already failing; the original cause is more useful than this.
        }
    }
}
