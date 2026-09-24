package com.harness.expensecapture.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an override did, derived from item identity rather than from the change in count.
 *
 * <p>The mixed-operation test is the reason this replaced a count-based EDIT/MERGE/SPLIT enum: one request can
 * split an item and edit others at the same time, and a single verb cannot describe that without lying.
 */
@DisplayName("OverrideSummary")
class OverrideSummaryTest {

    private static final String EUR = "EUR";

    private LineItem item(final String description, final String amount) {
        return LineItem.create(description, Money.of(amount, EUR));
    }

    private LineItem sameIdAs(final LineItem original, final String amount) {
        return new LineItem(original.id(), original.description(), Money.of(amount, EUR), null, null);
    }

    @Test
    void should_of_withAllIdsRetained_reportEveryItemKept() {
        final LineItem a = item("Espresso", "3.50");
        final LineItem b = item("Sandwich", "8.90");

        final OverrideSummary summary = OverrideSummary.of(
                List.of(a, b), List.of(sameIdAs(a, "3.00"), sameIdAs(b, "9.40")));

        assertThat(summary).isEqualTo(new OverrideSummary(2, 2, 2, 0, 0));
    }

    @Test
    void should_of_withItemsCombinedIntoANewOne_reportRemovedAndCreated() {
        final LineItem a = item("Espresso", "3.50");
        final LineItem b = item("Sandwich", "8.90");

        final OverrideSummary summary = OverrideSummary.of(
                List.of(a, b), List.of(item("Combined", "12.40")));

        assertThat(summary).isEqualTo(new OverrideSummary(2, 1, 0, 1, 2));
    }

    @Test
    void should_of_withOneItemSplitKeepingItsId_reportOneKeptAndOneCreated() {
        final LineItem a = item("Sandwich", "8.90");

        final OverrideSummary summary = OverrideSummary.of(
                List.of(a), List.of(sameIdAs(a, "4.45"), item("Sandwich half B", "4.45")));

        assertThat(summary).isEqualTo(new OverrideSummary(1, 2, 1, 1, 0));
    }

    @Test
    void should_of_withSplitAndEditTogether_describeBothRatherThanPickOneVerb() {
        // The case a count-based verb got wrong: the sandwich is split AND the other two rows are edited.
        // The old enum reported SPLIT and hid the two edits entirely.
        final LineItem espresso = item("Espresso", "3.50");
        final LineItem sandwich = item("Sandwich", "8.90");
        final LineItem water = item("Mineral water", "2.60");

        final OverrideSummary summary = OverrideSummary.of(
                List.of(espresso, sandwich, water),
                List.of(sameIdAs(espresso, "3.00"),
                        sameIdAs(sandwich, "4.45"),
                        item("Sandwich half B", "4.45"),
                        sameIdAs(water, "3.10")));

        assertThat(summary.before()).isEqualTo(3);
        assertThat(summary.after()).isEqualTo(4);
        assertThat(summary.kept()).as("three rows kept their identity, so three were edits").isEqualTo(3);
        assertThat(summary.created()).as("only the second half is a new item").isEqualTo(1);
        assertThat(summary.removed()).isZero();
    }

    @Test
    void should_of_withEveryItemReplaced_reportNothingKept() {
        // Same count as before, but no identity survives: not an "edit" in any meaningful sense.
        final OverrideSummary summary = OverrideSummary.of(
                List.of(item("Old A", "5.00"), item("Old B", "5.00")),
                List.of(item("New A", "5.00"), item("New B", "5.00")));

        assertThat(summary).isEqualTo(new OverrideSummary(2, 2, 0, 2, 2));
    }

    @Test
    void should_of_withItemsClearedEntirely_reportAllRemoved() {
        final OverrideSummary summary = OverrideSummary.of(
                List.of(item("Espresso", "3.50")), List.of());

        assertThat(summary).isEqualTo(new OverrideSummary(1, 0, 0, 0, 1));
    }

    @Test
    void should_of_withNoItemsBefore_reportEverythingCreated() {
        final OverrideSummary summary = OverrideSummary.of(
                List.of(), List.of(item("Espresso", "3.50"), item("Sandwich", "8.90")));

        assertThat(summary).isEqualTo(new OverrideSummary(0, 2, 0, 2, 0));
    }
}
