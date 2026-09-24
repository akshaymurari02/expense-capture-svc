package com.harness.expensecapture.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * User override for a transaction's line items, supplied as the complete replacement list.
 *
 * <p>Edit, merge and split are all expressible this way: a merge sends fewer items, a split sends more, an
 * edit sends changed amounts. One code path therefore covers all three verbs.
 */
public record UpdateItemsRequest(
        @NotNull(message = "items is required")
        @Valid
        List<LineItemInput> items) {
}
