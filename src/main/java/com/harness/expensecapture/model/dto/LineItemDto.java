package com.harness.expensecapture.model.dto;

import com.harness.expensecapture.model.domain.LineItem;
import java.math.BigDecimal;

/** A line item as returned by the API. */
public record LineItemDto(
        String itemId,
        String description,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal quantity) {

    public static LineItemDto from(final LineItem item) {
        return new LineItemDto(item.id(), item.description(), item.amount().amount(),
                item.taxAmount() == null ? null : item.taxAmount().amount(), item.quantity());
    }
}
