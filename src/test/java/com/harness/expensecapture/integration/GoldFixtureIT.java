package com.harness.expensecapture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.harness.expensecapture.model.domain.Transaction;
import com.harness.expensecapture.repository.TransactionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Every fixture must reproduce the fields in {@code gold.json} end to end. */
@DisplayName("Fixture extraction matches gold.json")
class GoldFixtureIT extends AbstractReceiptFlowIT {

    @Autowired
    private TransactionRepository transactionRepository;

    private void assertMatchesGold(final JsonNode actual, final String goldKey) {
        final JsonNode expected = gold.get(goldKey);

        assertThat(actual.get("merchant").asText()).isEqualTo(expected.get("merchant").asText());
        assertThat(actual.get("date").asText()).isEqualTo(expected.get("date").asText());
        assertThat(actual.get("currency").asText()).isEqualTo(expected.get("currency").asText());
        assertThat(new BigDecimal(actual.get("grand_total").asText()))
                .isEqualByComparingTo(new BigDecimal(expected.get("grand_total").asText()));
        assertThat(actual.get("itemize_status").asText())
                .isEqualTo(expected.get("itemize_status").asText());

        final JsonNode expectedTaxes = expected.get("taxes");
        final JsonNode actualTaxes = actual.get("taxes");
        assertThat(actualTaxes).hasSize(expectedTaxes.size());
        for (int i = 0; i < expectedTaxes.size(); i++) {
            assertThat(actualTaxes.get(i).get("name").asText())
                    .isEqualTo(expectedTaxes.get(i).get("name").asText());
            assertThat(new BigDecimal(actualTaxes.get(i).get("rate").asText()))
                    .isEqualByComparingTo(new BigDecimal(expectedTaxes.get(i).get("rate").asText()));
            assertThat(new BigDecimal(actualTaxes.get(i).get("amount").asText()))
                    .isEqualByComparingTo(new BigDecimal(expectedTaxes.get(i).get("amount").asText()));
        }

        final JsonNode expectedItems = expected.get("line_items");
        final JsonNode actualItems = actual.get("line_items");
        assertThat(actualItems).hasSize(expectedItems.size());
        for (int i = 0; i < expectedItems.size(); i++) {
            assertThat(actualItems.get(i).get("description").asText())
                    .isEqualTo(expectedItems.get(i).get("description").asText());
            assertThat(new BigDecimal(actualItems.get(i).get("amount").asText()))
                    .isEqualByComparingTo(new BigDecimal(expectedItems.get(i).get("amount").asText()));
        }
    }

    @Test
    void should_process_withCleanReceipt_matchGoldAndReturnComplete() throws Exception {
        final JsonNode transaction = processFixture("receipt-clean");

        assertMatchesGold(transaction, "receipt-clean");
        assertThat(transaction.get("itemize_status").asText()).isEqualTo("COMPLETE");
        assertThat(transaction.get("line_item_basis").asText()).isEqualTo("NET");
    }

    @Test
    void should_process_withTaxOnlyReceipt_matchGoldAndReturnNeedsReview() throws Exception {
        final JsonNode transaction = processFixture("receipt-tax-only");

        assertMatchesGold(transaction, "receipt-tax-only");
        assertThat(transaction.get("line_items")).isEmpty();
        assertThat(transaction.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        // Printed 3.83, not a naive 24.00 * 0.19 = 4.56.
        assertThat(new BigDecimal(transaction.get("taxes").get(0).get("amount").asText()))
                .isEqualByComparingTo("3.83");
    }

    @Test
    void should_process_withMismatchedReceipt_matchGoldAndReturnNeedsReview() throws Exception {
        final JsonNode transaction = processFixture("receipt-mismatch");

        assertMatchesGold(transaction, "receipt-mismatch");
        assertThat(transaction.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(transaction.get("reconciliation").get("difference").asText()))
                .isEqualByComparingTo("6.60");
    }

    @Test
    void should_process_withMismatchedReceipt_notAddBalancingLineOrAlterTotal() throws Exception {
        final JsonNode transaction = processFixture("receipt-mismatch");

        // The brief's hard rule: keep the transaction, flag it, invent nothing.
        assertThat(transaction.get("line_items")).hasSize(2);
        for (final JsonNode item : transaction.get("line_items")) {
            assertThat(new BigDecimal(item.get("amount").asText())).isNotEqualByComparingTo("6.60");
        }
        // Gold: must NOT rewrite grand_total to 11.90 or 10.00.
        assertThat(new BigDecimal(transaction.get("grand_total").asText())).isEqualByComparingTo("18.50");
    }

    @Test
    void should_process_withAnyFixture_persistRawOcrTextAndStructuredFields() throws Exception {
        final JsonNode transaction = processFixture("receipt-clean");

        assertThat(transaction.get("ocr").get("provider").asText()).isEqualTo("stub");
        assertThat(transaction.get("ocr").get("text_length").asInt()).isPositive();
        assertThat(transaction.get("receipt_id").asText()).isNotBlank();
    }

    @Test
    void should_process_storeRawOcrTextInTheAggregate_evenThoughItIsNotExposed() throws Exception {
        final JsonNode response = processFixture("receipt-clean");
        final String transactionId = response.get("transaction_id").asText();

        // The API deliberately hides raw OCR, so assert persistence at the repository instead: the brief
        // requires the text to be stored and to be the source for re-itemize.
        final Transaction stored = transactionRepository.findById(transactionId).orElseThrow();

        assertThat(stored.hasStoredOcrText()).isTrue();
        assertThat(stored.getRawOcrText()).contains("Cafe Mitte").contains("Espresso");
        assertThat(stored.getOcrProvider()).isEqualTo("stub");
        assertThat(response.get("ocr").get("text_length").asInt())
                .as("reported length must match the text actually stored")
                .isEqualTo(stored.getRawOcrText().length());
    }
}
