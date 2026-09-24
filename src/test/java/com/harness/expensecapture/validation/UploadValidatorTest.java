package com.harness.expensecapture.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** Each upload rule in isolation. */
@DisplayName("Upload validators")
class UploadValidatorTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 pretending to be a png".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TEXT_BYTES = "MERCHANT: Cafe Mitte".getBytes(StandardCharsets.UTF_8);

    private AppProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getStorage().setMaxFileSize(DataSize.ofKilobytes(10));
        properties.getStorage().setAllowedTypes(List.of("text/plain", "image/png", "application/pdf"));
    }

    private ReceiptUpload upload(final String filename, final String contentType, final byte[] content) {
        return ReceiptUpload.open(new MockMultipartFile("file", filename, contentType, content),
                com.harness.expensecapture.storage.FileSignature.maxMagicLength());
    }

    @Test
    void should_open_withEmptyFile_throwFileMissing() {
        assertThatThrownBy(() -> upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.FILE_MISSING);
    }

    @Test
    void should_open_withNullFile_throwFileMissing() {
        assertThatThrownBy(() -> ReceiptUpload.open(null, 8))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCodes.FILE_MISSING);
    }

    @Test
    void should_head_whenReadTwice_returnSameBytesAndLeaveStreamAtStart() throws Exception {
        try (ReceiptUpload upload = upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, TEXT_BYTES)) {
            assertThat(upload.head()).isEqualTo(upload.head());
            // The store must still see the whole payload from byte zero after validators inspected the head.
            assertThat(upload.content().readAllBytes()).isEqualTo(TEXT_BYTES);
        }
    }

    @Test
    void should_head_whenMutatedByCaller_notAffectTheUpload() throws Exception {
        try (ReceiptUpload upload = upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, TEXT_BYTES)) {
            final byte[] first = upload.head();
            first[0] = 0;

            assertThat(upload.head()[0]).isNotZero();
        }
    }

    @Test
    void should_validate_withOversizedFile_throwUnsupported() throws Exception {
        properties.getStorage().setMaxFileSize(DataSize.ofBytes(4));
        final FileSizeValidator validator = new FileSizeValidator(properties);

        try (ReceiptUpload upload = upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, TEXT_BYTES)) {
            assertThatThrownBy(() -> validator.validate(upload))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCodes.FILE_UNSUPPORTED);
        }
    }

    @Test
    void should_validate_withFileAtExactlyTheLimit_pass() throws Exception {
        properties.getStorage().setMaxFileSize(DataSize.ofBytes(TEXT_BYTES.length));
        final FileSizeValidator validator = new FileSizeValidator(properties);

        try (ReceiptUpload upload = upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, TEXT_BYTES)) {
            assertThatCode(() -> validator.validate(upload)).doesNotThrowAnyException();
        }
    }

    @Test
    void should_validate_withDisallowedContentType_throwUnsupported() throws Exception {
        final DeclaredContentTypeValidator validator = new DeclaredContentTypeValidator(properties);

        try (ReceiptUpload upload = upload("receipt.gif", "image/gif", TEXT_BYTES)) {
            assertThatThrownBy(() -> validator.validate(upload))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCodes.FILE_UNSUPPORTED);
        }
    }

    @Test
    void should_validate_withCharsetParameterOnContentType_stillMatchAllowlist() throws Exception {
        final DeclaredContentTypeValidator validator = new DeclaredContentTypeValidator(properties);

        try (ReceiptUpload upload = upload("receipt.txt", "text/plain; charset=UTF-8", TEXT_BYTES)) {
            assertThatCode(() -> validator.validate(upload)).doesNotThrowAnyException();
        }
    }

    @Test
    void should_validate_withPdfBytesDeclaredAsPng_throwContentMismatch() throws Exception {
        final FileContentValidator validator = new FileContentValidator();

        try (ReceiptUpload upload = upload("invoice.png", MediaType.IMAGE_PNG_VALUE, PDF_BYTES)) {
            assertThatThrownBy(() -> validator.validate(upload))
                    .isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("code", ErrorCodes.FILE_CONTENT_MISMATCH);
        }
    }

    @Test
    void should_validate_withPlainTextHavingNoSignature_pass() throws Exception {
        final FileContentValidator validator = new FileContentValidator();

        try (ReceiptUpload upload = upload("receipt.txt", MediaType.TEXT_PLAIN_VALUE, TEXT_BYTES)) {
            assertThatCode(() -> validator.validate(upload)).doesNotThrowAnyException();
        }
    }

    @Test
    void should_validate_withPdfBytesDeclaredAsPdf_pass() throws Exception {
        final FileContentValidator validator = new FileContentValidator();

        try (ReceiptUpload upload = upload("receipt.pdf", MediaType.APPLICATION_PDF_VALUE, PDF_BYTES)) {
            assertThatCode(() -> validator.validate(upload)).doesNotThrowAnyException();
        }
    }
}
