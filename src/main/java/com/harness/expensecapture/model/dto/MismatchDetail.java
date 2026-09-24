package com.harness.expensecapture.model.dto;

import com.harness.expensecapture.model.domain.LineItemBasis;
import java.math.BigDecimal;

/** Explains a reconciliation failure to the caller instead of silently adjusting totals. */
public record MismatchDetail(
        BigDecimal expected,
        BigDecimal actual,
        BigDecimal difference,
        LineItemBasis lineItemBasis,
        String currency) {
}
