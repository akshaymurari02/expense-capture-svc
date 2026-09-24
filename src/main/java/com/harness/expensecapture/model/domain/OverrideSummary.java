package com.harness.expensecapture.model.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What an override actually did to a transaction's line items, derived from their ids.
 *
 * <p>Replaces an earlier {@code OverrideKind} enum that classified the request as EDIT, MERGE or SPLIT purely
 * from the change in item count. That was not merely redundant, it was wrong: one request can split an item
 * <em>and</em> edit other rows at the same time, and a count-based verb reports only the cardinality change
 * while hiding the rest. Splitting one of three items and editing the other two was logged as {@code SPLIT},
 * which sends anyone reading the log the wrong way.
 *
 * <p>These four numbers are facts rather than interpretations, and they describe mixed operations correctly.
 * The brief's three verbs describe <em>user intent</em>, which a full-replacement payload does not carry, so
 * the server does not pretend to recover it.
 *
 * @param before  items the transaction had
 * @param after   items it has now
 * @param kept    items whose identity survived, i.e. edits
 * @param created items that did not exist before
 * @param removed items that are gone
 */
public record OverrideSummary(int before, int after, int kept, int created, int removed) {

    public static OverrideSummary of(final List<LineItem> previousItems, final List<LineItem> newItems) {
        final Set<String> previousIds = previousItems.stream()
                .map(LineItem::id)
                .collect(Collectors.toSet());
        final Set<String> newIds = newItems.stream()
                .map(LineItem::id)
                .collect(Collectors.toSet());

        final int kept = (int) newIds.stream().filter(previousIds::contains).count();
        return new OverrideSummary(
                previousItems.size(),
                newItems.size(),
                kept,
                newItems.size() - kept,
                previousItems.size() - kept);
    }
}
