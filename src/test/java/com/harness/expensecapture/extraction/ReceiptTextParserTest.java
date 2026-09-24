package com.harness.expensecapture.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.model.domain.LineItem;
import com.harness.expensecapture.model.domain.LineItemBasis;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parser behaviour against the three shipped fixtures and the values in {@code gold.json}. */
class ReceiptTextParserTest {

    private static final Path FIXTURES = Path.of("fixtures", "task-a");

    private ReceiptTextParser parser;

    @BeforeEach
    void setUp() {
        parser = new ReceiptTextParser();
    }

    private String fixture(final String name) throws IOException {
        return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    void should_parse_withCleanReceipt_returnGoldHeaderTaxesAndItems() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-clean.txt"));

        assertThat(result.merchant()).isEqualTo("Cafe Mitte");
        assertThat(result.transactionDate()).isEqualTo(LocalDate.of(2026, 3, 12));
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.grandTotal().amount()).isEqualByComparingTo("17.85");
        assertThat(result.printedSubtotal().amount()).isEqualByComparingTo("15.00");
        assertThat(result.headerUsable()).isTrue();

        assertThat(result.taxes()).hasSize(1);
        assertThat(result.taxes().get(0).name()).isEqualTo("VAT");
        assertThat(result.taxes().get(0).rate()).isEqualByComparingTo("0.19");
        assertThat(result.taxes().get(0).amount().amount()).isEqualByComparingTo("2.85");

        assertThat(result.lineItems()).extracting(LineItem::description)
                .containsExactly("Espresso", "Sandwich", "Mineral water");
        assertThat(result.lineItems()).extracting(item -> item.amount().amount())
                .containsExactly(new BigDecimal("3.50"), new BigDecimal("8.90"), new BigDecimal("2.60"));
    }

    @Test
    void should_parse_withCleanReceipt_detectNetBasisFromPrintedSubtotal() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-clean.txt"));

        // Items sum to 15.00, which equals the printed Subtotal rather than the TOTAL, so they are net.
        assertThat(result.lineItemBasis()).isEqualTo(LineItemBasis.NET);
    }

    @Test
    void should_parse_withTaxOnlyReceipt_readPrintedTaxAmountNotDerived() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-tax-only.txt"));

        assertThat(result.merchant()).isEqualTo("Berlin Taxi GmbH");
        assertThat(result.grandTotal().amount()).isEqualByComparingTo("24.00");
        assertThat(result.printedSubtotal()).isNull();
        assertThat(result.taxes()).hasSize(1);
        // The printed value is 3.83. A naive total*rate would give 4.56, which gold rejects.
        assertThat(result.taxes().get(0).amount().amount()).isEqualByComparingTo("3.83");
        assertThat(result.taxes().get(0).amount().amount()).isNotEqualByComparingTo("4.56");
    }

    @Test
    void should_parse_withTaxOnlyReceipt_returnNoLineItemsAndUnknownBasis() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-tax-only.txt"));

        // "Trip fare" has no printed amount, so it is not an item. Gold expects an empty list; inventing an
        // amount that was never printed would contradict the brief.
        assertThat(result.lineItems()).isEmpty();
        assertThat(result.lineItemBasis()).isEqualTo(LineItemBasis.UNKNOWN);
    }

    @Test
    void should_parse_withMismatchedReceipt_readPrintedTaxRatherThanDeriveFromTotal() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-mismatch.txt"));

        assertThat(result.grandTotal().amount()).isEqualByComparingTo("18.50");
        assertThat(result.taxes()).hasSize(1);
        // Deriving from the gross total (18.50 * 0.19/1.19) would give 2.95; gold requires the printed 1.90.
        assertThat(result.taxes().get(0).amount().amount()).isEqualByComparingTo("1.90");
        assertThat(result.taxes().get(0).amount().amount()).isNotEqualByComparingTo("2.95");
    }

    @Test
    void should_parse_withMismatchedReceipt_keepPrintedTotalAndOnlyRealItems() throws IOException {
        final ExtractionResult result = parser.parse(fixture("receipt-mismatch.txt"));

        assertThat(result.merchant()).isEqualTo("Hotel Shop");
        assertThat(result.transactionDate()).isEqualTo(LocalDate.of(2026, 3, 13));
        assertThat(result.grandTotal().amount()).isEqualByComparingTo("18.50");
        assertThat(result.lineItems()).extracting(LineItem::description).containsExactly("Water", "Snacks");
    }

    @Test
    void should_parseLineItems_withSubtotalAndTaxLines_excludeThemFromItems() throws IOException {
        final var items = parser.parseLineItems(fixture("receipt-clean.txt"));

        assertThat(items).hasSize(3);
        assertThat(items).extracting(LineItem::description)
                .doesNotContain("Subtotal", "TOTAL", "VAT 19%");
    }

    @Test
    void should_parse_withMissingTaxAmountAndPrintedSubtotal_deriveFromSubtotal() {
        final String text = """
                MERCHANT: Derive Cafe
                DATE: 2026-03-12
                CURRENCY: EUR

                Coffee                      10.00

                Subtotal                   10.00
                VAT 19%
                TOTAL                      11.90
                """;

        final ExtractionResult result = parser.parse(text);

        // Fallback A: 10.00 * 0.19 = 1.90
        assertThat(result.taxes().get(0).amount().amount()).isEqualByComparingTo("1.90");
    }

    @Test
    void should_parse_withMissingTaxAmountAndNoSubtotal_deriveFromGrossTotal() {
        final String text = """
                MERCHANT: Gross Cafe
                DATE: 2026-03-12
                CURRENCY: EUR

                incl. VAT 19%
                TOTAL                      24.00
                """;

        final ExtractionResult result = parser.parse(text);

        // Fallback B: 24.00 * 0.19/1.19 = 3.83
        assertThat(result.taxes().get(0).amount().amount()).isEqualByComparingTo("3.83");
    }

    @Test
    void should_parse_withUnusableHeader_reportHeaderNotUsable() {
        final ExtractionResult result = parser.parse("some scanned noise with no labels at all");

        assertThat(result.headerUsable()).isFalse();
        assertThat(result.merchant()).isNull();
        assertThat(result.grandTotal()).isNull();
    }

    @Test
    void should_parse_withGrossLineItems_detectGrossBasis() {
        final String text = """
                MERCHANT: Gross Lines Ltd
                DATE: 2026-03-12
                CURRENCY: EUR

                Bundle                      11.90

                VAT 19%                     1.90
                TOTAL                      11.90
                """;

        final ExtractionResult result = parser.parse(text);

        // Items sum to the TOTAL rather than a subtotal, so they already include tax.
        assertThat(result.lineItemBasis()).isEqualTo(LineItemBasis.GROSS);
    }
}
