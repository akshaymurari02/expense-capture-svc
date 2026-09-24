package com.harness.expensecapture.ocr;

import com.harness.expensecapture.config.AppProperties;
import com.harness.expensecapture.exception.ErrorCodes;
import com.harness.expensecapture.exception.UnprocessableException;
import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.util.LogConstants;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stubbed OCR: no vendor is called. Resolution order:
 *
 * <ol>
 *   <li>fixture text named after the uploaded file's stem, e.g. {@code receipt-clean.png} to
 *       {@code fixtures/task-a/receipt-clean.txt};
 *   <li>the stored file itself, when it is decodable UTF-8 text.
 * </ol>
 *
 * Anything else is an {@code EXP-EXT-001} failure rather than a silent empty result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StubOcrProvider implements OcrProvider {

    private static final String PROVIDER_NAME = "stub";

    private final AppProperties properties;

    @Override
    public String extractText(final Receipt receipt) {
        final String fromFixture = readFixture(receipt.filenameStem());
        if (fromFixture != null) {
            log.info("{} Stub OCR resolved fixture text. stem={} chars={}{}", LogConstants.SVC,
                    receipt.filenameStem(), fromFixture.length(), LogConstants.id(receipt.id()));
            return fromFixture;
        }

        final String fromUpload = readUploadedAsText(receipt.storedPath());
        if (fromUpload != null) {
            log.info("{} Stub OCR read uploaded file as text. chars={}{}", LogConstants.SVC,
                    fromUpload.length(), LogConstants.id(receipt.id()));
            return fromUpload;
        }

        throw new UnprocessableException(ErrorCodes.OCR_FAILED,
                "Stub OCR cannot resolve text for '" + receipt.originalFilename()
                        + "'. Upload a text receipt, or name the file after a fixture in "
                        + properties.getOcr().getFixtureDir() + ".");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private String readFixture(final String stem) {
        if (stem == null || stem.isBlank()) {
            return null;
        }
        final Path fixture = Paths.get(properties.getOcr().getFixtureDir())
                .toAbsolutePath()
                .normalize()
                .resolve(stem + ".txt");
        if (!Files.isRegularFile(fixture)) {
            return null;
        }
        try {
            final String text = Files.readString(fixture, StandardCharsets.UTF_8);
            return text.isBlank() ? null : text;
        } catch (final IOException e) {
            throw new UnprocessableException(ErrorCodes.OCR_FAILED,
                    "Failed to read fixture text " + fixture.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private String readUploadedAsText(final Path storedPath) {
        if (storedPath == null || !Files.isRegularFile(storedPath)) {
            return null;
        }
        try {
            final var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            final String text = decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(storedPath))).toString();
            return text.isBlank() ? null : text;
        } catch (final CharacterCodingException e) {
            log.debug("{} Uploaded file is not decodable text; stub OCR cannot use it.",
                    LogConstants.SVC);
            return null;
        } catch (final IOException e) {
            throw new UnprocessableException(ErrorCodes.OCR_FAILED,
                    "Failed to read stored receipt file: " + e.getMessage(), e);
        }
    }
}
