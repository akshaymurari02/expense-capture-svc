package com.harness.expensecapture.model.dto;

import com.harness.expensecapture.model.domain.Tax;
import java.math.BigDecimal;

/** A tax row as returned by the API. */
public record TaxDto(String taxId, String name, BigDecimal rate, BigDecimal amount, String jurisdiction) {

    public static TaxDto from(final Tax tax) {
        return new TaxDto(tax.id(), tax.name(), tax.rate(), tax.amount().amount(), tax.jurisdiction());
    }
}
