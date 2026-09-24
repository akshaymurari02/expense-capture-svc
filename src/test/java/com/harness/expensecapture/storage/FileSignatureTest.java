package com.harness.expensecapture.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Magic-number detection, which is what makes a spoofed Content-Type detectable. */
@DisplayName("FileSignature")
class FileSignatureTest {

    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] PDF_HEAD = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    @Test
    void should_detect_withPngMagic_returnPng() {
        assertThat(FileSignature.detect(PNG_HEAD)).isEqualTo(FileSignature.PNG);
    }

    @Test
    void should_detect_withJpegMagic_returnJpeg() {
        assertThat(FileSignature.detect(JPEG_HEAD)).isEqualTo(FileSignature.JPEG);
    }

    @Test
    void should_detect_withPdfMagic_returnPdf() {
        assertThat(FileSignature.detect(PDF_HEAD)).isEqualTo(FileSignature.PDF);
    }

    @Test
    void should_detect_withPlainText_returnNullSoTextUploadsStayAllowed() {
        assertThat(FileSignature.detect("MERCHANT: Cafe Mitte".getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    void should_detect_withTruncatedMagic_returnNullRatherThanThrow() {
        assertThat(FileSignature.detect(new byte[] {(byte) 0x89, 'P'})).isNull();
        assertThat(FileSignature.detect(new byte[0])).isNull();
        assertThat(FileSignature.detect(null)).isNull();
    }

    @Test
    void should_matchesDeclared_withPdfBytesDeclaredAsPng_returnFalse() {
        assertThat(FileSignature.PDF.matchesDeclared("image/png")).isFalse();
    }

    @Test
    void should_matchesDeclared_withJpegDeclaredAsImageJpg_returnTrue() {
        // curl and some browsers send image/jpg; rejecting it would break legitimate uploads.
        assertThat(FileSignature.JPEG.matchesDeclared("image/jpg")).isTrue();
        assertThat(FileSignature.JPEG.matchesDeclared("image/jpeg")).isTrue();
    }

    @Test
    void should_matchesDeclared_withOctetStream_returnTrueForAnySignature() {
        assertThat(FileSignature.PNG.matchesDeclared("application/octet-stream")).isTrue();
        assertThat(FileSignature.PDF.matchesDeclared("application/octet-stream")).isTrue();
    }

    @Test
    void should_matchesDeclared_withNullOrBlank_returnFalse() {
        assertThat(FileSignature.PNG.matchesDeclared(null)).isFalse();
        assertThat(FileSignature.PNG.matchesDeclared("  ")).isFalse();
    }

    @Test
    void should_maxMagicLength_coverTheLongestSignature() {
        assertThat(FileSignature.maxMagicLength()).isEqualTo(PNG_HEAD.length);
    }
}
