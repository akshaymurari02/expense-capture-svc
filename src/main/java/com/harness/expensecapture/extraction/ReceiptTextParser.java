package com.harness.expensecapture.extraction;

import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import com.harness.expensecapture.model.domain.Money;
import com.harness.expensecapture.model.domain.Tax;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns raw receipt text into structured fields. Line-oriented and deliberately conservative: a line only
 * becomes an item when it carries both a description and an amount.
 *
 * <p>Tax amounts are <strong>read</strong> from the receipt whenever printed. Derivation is a fallback only,
 * because deriving from the gross total is wrong whenever the printed subtotal and total disagree.
 *
 * <p>Intentionally a concrete class behind no interface, and intentionally unaware of {@code OcrProvider}.
 * It is a pure function {@code String -> ExtractionResult}: no I/O, no clock, no network, so there is no seam
 * worth stubbing and no second implementation in prospect. Keeping text <em>retrieval</em> out of it is what
 * lets {@code POST /transactions/{id}/itemize} re-parse <em>stored</em> OCR text without invoking OCR again,
 * as the brief requires.
 */
@Component
public class ReceiptTextParser {

    /** ISO 4217 code for "no currency", used when the receipt does not state one. */
    private static final String UNKNOWN_CURRENCY = "XXX";

    private static final Pattern MERCHANT = Pattern.compile("^\\s*MERCHANT\\s*:\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("^\\s*DATE\\s*:\\s*(\\d{4}-\\d{2}-\\d{2})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY = Pattern.compile("^\\s*CURRENCY\\s*:\\s*([A-Za-z]{3})\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBTOTAL = Pattern.compile("^\\s*SUB-?\\s?TOTAL\\s+([\\d.,]+)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile("^\\s*(?:GRAND\\s+)?TOTAL\\s+([\\d.,]+)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAX_ROW = Pattern.compile(
            "^\\s*(?:incl\\.?|including|inclusive)?\\s*(VAT|GST|MWST|TAX|SALES\\s+TAX)\\s*"
                    + "(\\d{1,2}(?:[.,]\\d+)?)\\s*%\\s*([\\d.,]+)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM = Pattern.compile("^\\s*(?<desc>\\D[^\\d]*?)\\s{2,}(?<amt>\\d+[.,]\\d{2})\\s*$");
    private static final Pattern NON_ITEM_LABEL = Pattern.compile(
            "^\\s*(sub-?\\s?total|total|vat|gst|mwst|tax|tip|gratuity|service\\s+charge|change|cash|card|balance"
                    + "|amount\\s+due|rounding)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal BASIS_TOLERANCE = new BigDecimal("0.01");

    public ExtractionResult parse(final String rawText) {
        final List<String> lines = toLines(rawText);
        final String currency = firstMatch(lines, CURRENCY).map(String::toUpperCase).orElse(UNKNOWN_CURRENCY);
        final Money grandTotal = firstMatch(lines, TOTAL).map(raw -> money(raw, currency)).orElse(null);
        final Money printedSubtotal = firstMatch(lines, SUBTOTAL).map(raw -> money(raw, currency)).orElse(null);

        final List<Tax> taxes = parseTaxes(lines, currency, grandTotal, printedSubtotal);
        final List<LineItem> items = collectLineItems(lines, currency);
        final LineItemBasis basis = detectBasis(items, printedSubtotal, grandTotal, currency);

        return new ExtractionResult(
                firstMatch(lines, MERCHANT).orElse(null),
                firstMatch(lines, DATE).map(this::parseDate).orElse(null),
                currency,
                grandTotal,
                printedSubtotal,
                basis,
                taxes,
                items);
    }

    /** Re-derives only the line items, for the re-itemize path. */
    public List<LineItem> parseLineItems(final String rawText) {
        final List<String> lines = toLines(rawText);
        final String currency = firstMatch(lines, CURRENCY).map(String::toUpperCase).orElse(UNKNOWN_CURRENCY);
        return collectLineItems(lines, currency);
    }

    private List<Tax> parseTaxes(final List<String> lines, final String currency, final Money grandTotal,
            final Money printedSubtotal) {
        final List<Tax> taxes = new ArrayList<>();
        for (final String line : lines) {
            final Matcher matcher = TAX_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            final String name = matcher.group(1).toUpperCase().replaceAll("\\s+", " ");
            final BigDecimal rate = new BigDecimal(matcher.group(2).replace(',', '.')).divide(HUNDRED);
            final Money amount = resolveTaxAmount(matcher.group(3), rate, currency, grandTotal, printedSubtotal);
            if (amount != null) {
                taxes.add(Tax.create(name, rate, amount));
            }
        }
        return taxes;
    }

    /**
     * The printed amount always wins. Fallbacks apply only when the receipt omits it: from the printed
     * subtotal if available, otherwise from the gross total.
     */
    private Money resolveTaxAmount(final String printedAmount, final BigDecimal rate, final String currency,
            final Money grandTotal, final Money printedSubtotal) {
        if (printedAmount != null && !printedAmount.isBlank()) {
            return money(printedAmount, currency);
        }
        if (printedSubtotal != null) {
            return Money.of(printedSubtotal.amount().multiply(rate), currency);
        }
        if (grandTotal != null) {
            final BigDecimal grossFactor = rate.divide(ONE.add(rate), 10, Money.ROUNDING);
            return Money.of(grandTotal.amount().multiply(grossFactor), currency);
        }
        return null;
    }

    private List<LineItem> collectLineItems(final List<String> lines, final String currency) {
        final List<LineItem> items = new ArrayList<>();
        for (final String line : lines) {
            final Matcher matcher = ITEM.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            final String description = matcher.group("desc").trim();
            if (description.isEmpty() || NON_ITEM_LABEL.matcher(description).find()) {
                continue;
            }
            items.add(LineItem.create(description, money(matcher.group("amt"), currency)));
        }
        return items;
    }

    /**
     * Decides whether item amounts exclude or include tax by comparing their sum against the printed
     * subtotal and total. Never alters the amounts themselves.
     */
    private LineItemBasis detectBasis(final List<LineItem> items, final Money printedSubtotal,
            final Money grandTotal, final String currency) {
        if (items.isEmpty()) {
            return LineItemBasis.UNKNOWN;
        }
        Money sum = Money.zero(currency);
        for (final LineItem item : items) {
            sum = sum.add(item.amount());
        }
        if (printedSubtotal != null && sum.equalsWithin(printedSubtotal, BASIS_TOLERANCE)) {
            return LineItemBasis.NET;
        }
        if (grandTotal != null && sum.equalsWithin(grandTotal, BASIS_TOLERANCE)) {
            return LineItemBasis.GROSS;
        }
        return LineItemBasis.NET;
    }

    private List<String> toLines(final String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        return List.of(rawText.split("\\R"));
    }

    private java.util.Optional<String> firstMatch(final List<String> lines, final Pattern pattern) {
        for (final String line : lines) {
            final Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return java.util.Optional.of(matcher.group(1).trim());
            }
        }
        return java.util.Optional.empty();
    }

    private LocalDate parseDate(final String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private Money money(final String raw, final String currency) {
        return Money.of(AmountFormat.toBigDecimal(raw), currency);
    }
}
