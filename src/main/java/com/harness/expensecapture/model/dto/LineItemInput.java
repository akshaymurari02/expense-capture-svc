package com.harness.expensecapture.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One user-supplied line item in an override request.
 *
 * @param itemId optional. Supply an existing {@code item_id} to <em>edit</em> that item and keep its identity;
 *               omit it for a genuinely new item, as produced by a merge or a split. An id that does not
 *               belong to the transaction is rejected rather than silently treated as new.
 */
public record LineItemInput(
        String itemId,

        @NotBlank(message = "description must not be blank")
        @Size(max = 255, message = "description must be at most 255 characters")
        String description,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", message = "amount must not be negative")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @DecimalMin(value = "0.00", message = "tax_amount must not be negative")
        @Digits(integer = 10, fraction = 2, message = "tax_amount must have at most 2 decimal places")
        BigDecimal taxAmount,

        @DecimalMin(value = "0.01", message = "quantity must be positive")
        @Digits(integer = 6, fraction = 3, message = "quantity must have at most 3 decimal places")
        BigDecimal quantity) {
}
