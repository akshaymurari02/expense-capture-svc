package com.harness.expensecapture.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The three override verbs, derived from how the item count changed. */
@DisplayName("OverrideKind")
class OverrideKindTest {

    @ParameterizedTest
    @CsvSource({
            "3, 3, EDIT",
            "1, 1, EDIT",
            "0, 0, EDIT",
            "3, 1, MERGE",
            "2, 1, MERGE",
            "1, 3, SPLIT",
            "0, 2, SPLIT",
    })
    void should_of_withCountChange_deriveTheVerb(final int before, final int after, final OverrideKind expected) {
        assertThat(OverrideKind.of(before, after)).isEqualTo(expected);
    }

    @Test
    void should_of_withItemsClearedEntirely_reportMerge() {
        // Emptying the list removes items, so it is a merge rather than an edit of zero things.
        assertThat(OverrideKind.of(3, 0)).isEqualTo(OverrideKind.MERGE);
    }
}
