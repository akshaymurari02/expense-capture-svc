package com.harness.expensecapture.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Sanitising of client-supplied filenames. */
@DisplayName("StoredFilename")
class StoredFilenameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config",
            "/absolute/path/receipt.png",
            "C:\\Users\\bob\\receipt.png",
    })
    void should_from_withPathTraversalAttempt_keepOnlyTheFinalSegment(final String hostile) {
        final StoredFilename filename = StoredFilename.from(hostile);

        assertThat(filename.value())
                .doesNotContain("..")
                .doesNotContain("/")
                .doesNotContain("\\");
    }

    @Test
    void should_from_withShellMetacharacters_replaceThemWithUnderscore() {
        // Space, '$' and '(' are each replaced, hence three underscores between "ceipt" and "whoami".
        assertThat(StoredFilename.from("re;ceipt $(whoami).png").value()).isEqualTo("re_ceipt___whoami_.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "..", ".", "///", "\\\\"})
    void should_from_withUnusableName_fallBackToReceipt(final String unusable) {
        assertThat(StoredFilename.from(unusable).value()).isEqualTo("receipt");
    }

    @Test
    void should_from_withNull_fallBackToReceipt() {
        assertThat(StoredFilename.from(null).value()).isEqualTo("receipt");
    }

    @Test
    void should_from_withOverlongName_truncateTo120Characters() {
        final StoredFilename filename = StoredFilename.from("a".repeat(500) + ".png");

        assertThat(filename.value()).hasSize(120);
    }

    @Test
    void should_from_withValidName_leaveItUnchanged() {
        assertThat(StoredFilename.from("receipt-clean.txt").value()).isEqualTo("receipt-clean.txt");
    }

    @ParameterizedTest
    @CsvSource({
            "receipt-clean.txt, receipt-clean",
            "receipt.tar.gz, receipt.tar",
            "no-extension, no-extension",
            ".hidden, .hidden",
    })
    void should_stem_withVariousNames_dropOnlyTheFinalExtension(final String name, final String expectedStem) {
        assertThat(StoredFilename.from(name).stem()).isEqualTo(expectedStem);
    }
}
