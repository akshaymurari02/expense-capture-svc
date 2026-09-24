package com.harness.expensecapture.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Lifecycle rules: one transaction per receipt, itemize replaces items only, overrides reconcile or 409. */
@DisplayName("Transaction lifecycle")
class TransactionLifecycleIT extends AbstractReceiptFlowIT {

    @Test
    void should_process_calledTwice_returnSameTransactionAndNotDuplicate() throws Exception {
        final String receiptId = uploadFixture("receipt-clean");

        final JsonNode first = process(receiptId);
        final JsonNode second = process(receiptId);

        assertThat(second.get("transaction_id").asText()).isEqualTo(first.get("transaction_id").asText());
        assertThat(second.get("line_items")).hasSize(3);
        assertThat(second.get("taxes")).hasSize(1);
    }

    @Test
    void should_itemize_withStoredOcr_replaceLineItemsAndLeaveHeaderAndTaxesUnchanged() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();

        final JsonNode reitemized = itemize(transactionId);

        assertThat(reitemized.get("transaction_id").asText()).isEqualTo(transactionId);
        assertThat(reitemized.get("merchant").asText()).isEqualTo(processed.get("merchant").asText());
        assertThat(reitemized.get("grand_total").asText()).isEqualTo(processed.get("grand_total").asText());
        assertThat(reitemized.get("taxes").toString()).isEqualTo(processed.get("taxes").toString());
        assertThat(reitemized.get("line_items")).hasSize(3);
        assertThat(reitemized.get("itemize_status").asText()).isEqualTo("COMPLETE");
    }

    @Test
    void should_itemize_afterUserMergedItems_restoreTheMachineProposalFromStoredOcr() throws Exception {
        // This is what makes itemize more than a no-op when the OCR text never changes: it is undo for
        // auto-itemize. Same stored text, different result, because the line items changed underneath it.
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();
        assertThat(processed.get("line_items")).hasSize(3);

        patchItems(transactionId, "{\"items\":[{\"description\":\"Combined meal\",\"amount\":15.00}]}")
                .andExpect(status().isOk());
        assertThat(getTransaction(transactionId).get("line_items")).hasSize(1);

        final JsonNode reitemized = itemize(transactionId);

        assertThat(reitemized.get("line_items"))
                .as("re-itemize must rebuild the machine's proposal from stored OCR")
                .hasSize(3);
        assertThat(reitemized.get("line_items").get(0).get("description").asText()).isEqualTo("Espresso");
        assertThat(reitemized.get("transaction_id").asText()).isEqualTo(transactionId);
    }

    @Test
    void should_itemize_withMismatchedReceipt_stillNotInventABalancingLine() throws Exception {
        final JsonNode processed = processFixture("receipt-mismatch");

        final JsonNode reitemized = itemize(processed.get("transaction_id").asText());

        assertThat(reitemized.get("line_items")).hasSize(2);
        assertThat(reitemized.get("itemize_status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(new BigDecimal(reitemized.get("grand_total").asText())).isEqualByComparingTo("18.50");
    }

    @Test
    void should_updateItems_withValidMerge_returnCompleteAndPersist() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();

        // Merge three net items into one that still reconciles: 15.00 + 2.85 VAT = 17.85.
        final String body = """
                { "items": [ { "description": "Lunch", "amount": 15.00 } ] }
                """;

        patchItems(transactionId, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"))
                .andExpect(jsonPath("$.line_items.length()").value(1));

        final JsonNode reloaded = getTransaction(transactionId);
        assertThat(reloaded.get("line_items")).hasSize(1);
        assertThat(reloaded.get("itemize_status").asText()).isEqualTo("COMPLETE");
    }

    @Test
    void should_updateItems_withValidSplit_returnComplete() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");

        final String body = """
                { "items": [
                    { "description": "Drinks", "amount": 6.10 },
                    { "description": "Food", "amount": 8.90 }
                ] }
                """;

        patchItems(processed.get("transaction_id").asText(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"))
                .andExpect(jsonPath("$.line_items.length()").value(2));
    }

    @Test
    void should_updateItems_withNonReconcilingAmounts_returnConflictWithMismatchDetail() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();

        final String body = """
                { "items": [ { "description": "Lunch", "amount": 9.00 } ] }
                """;

        patchItems(transactionId, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXP-CON-001"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mismatch.expected").value(17.85))
                .andExpect(jsonPath("$.mismatch.actual").value(11.85))
                .andExpect(jsonPath("$.mismatch.difference").value(6.00));
    }

    @Test
    void should_updateItems_withNonReconcilingAmounts_notPersistAnything() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();

        patchItems(transactionId, """
                { "items": [ { "description": "Lunch", "amount": 9.00 } ] }
                """).andExpect(status().isConflict());

        // The rejected edit must leave the original auto-itemized data exactly as it was.
        final JsonNode reloaded = getTransaction(transactionId);
        assertThat(reloaded.get("line_items")).hasSize(3);
        assertThat(reloaded.get("itemize_status").asText()).isEqualTo("COMPLETE");
        assertThat(new BigDecimal(reloaded.get("grand_total").asText())).isEqualByComparingTo("17.85");
    }

    @Test
    void should_updateItems_withBlankDescription_returnBadRequest() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");

        patchItems(processed.get("transaction_id").asText(), """
                { "items": [ { "description": "  ", "amount": 15.00 } ] }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-003"));
    }

    @Test
    void should_updateItems_withMissingItemsField_returnBadRequest() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");

        patchItems(processed.get("transaction_id").asText(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-003"));
    }

    @Test
    void should_updateItems_withMalformedJsonBody_returnBadRequestNotServerError() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");

        // A caller's unparseable body must never surface as 5xx: retrying it can never succeed.
        patchItems(processed.get("transaction_id").asText(), "{\"items\": [ this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-006"));
    }

    @Test
    void should_updateItems_withWrongTypeForAmount_returnBadRequestWithoutLeakingInternals() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");

        final String body = "{\"items\":[{\"description\":\"X\","
                + "\"amount\":{\"amount\":\"not-a-number\",\"currency\":\"EUR\"}}]}";
        patchItems(processed.get("transaction_id").asText(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-006"))
                // Jackson's message names internal classes; the response must not echo it.
                .andExpect(jsonPath("$.message").value("Request body is missing or not valid JSON"));
    }

    @Test
    void should_updateItems_withWrongHttpMethod_returnMethodNotAllowedWithAllowHeader() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String id = processed.get("transaction_id").asText();

        mockMvc.perform(put("/api/v1/transactions/{id}/items", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("PATCH")))
                .andExpect(jsonPath("$.code").value("EXP-VAL-007"));
    }

    @Test
    void should_updateItems_withWrongContentType_returnUnsupportedMediaType() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String id = processed.get("transaction_id").asText();

        mockMvc.perform(patch("/api/v1/transactions/{id}/items", id)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("items"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("EXP-VAL-008"));
    }

    @Test
    void should_upload_withPdfBytesDeclaredAsPng_returnBadRequest() throws Exception {
        // Content-Type is client-supplied, so the declared type alone must never be trusted.
        final byte[] pdfBytes = "%PDF-1.7\nnot really a png".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final MockMultipartFile spoofed = new MockMultipartFile("file", "invoice.png",
                MediaType.IMAGE_PNG_VALUE, pdfBytes);

        mockMvc.perform(multipart("/api/v1/receipts").file(spoofed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-009"));
    }

    @Test
    void should_upload_withTraversalFilename_storeInsideUploadDirectoryOnly() throws Exception {
        final byte[] content = Files.readAllBytes(FIXTURES.resolve("receipt-clean.txt"));
        final MockMultipartFile hostile = new MockMultipartFile("file", "../../../../../../tmp/pwned.txt",
                MediaType.TEXT_PLAIN_VALUE, content);

        final String body = mockMvc.perform(multipart("/api/v1/receipts").file(hostile))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // The stored name must have lost every path component.
        assertThat(objectMapper.readTree(body).get("original_filename").asText()).isEqualTo("pwned.txt");
    }

    @Test
    void should_itemize_withUnusableHeader_keepFailedAndNotReportComplete() throws Exception {
        // Regression: re-itemize used to hardcode headerUsable=true, flipping a FAILED transaction to
        // COMPLETE even though it has no merchant and no date to reconcile against.
        final String receiptText = "CURRENCY: EUR\n\nEspresso 3.50\n\nSubtotal 3.50\nVAT 19% 0.67\nTOTAL 4.17\n";
        final MockMultipartFile file = new MockMultipartFile("file", "no-header.txt",
                MediaType.TEXT_PLAIN_VALUE, receiptText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final String receiptId = objectMapper.readTree(
                mockMvc.perform(multipart("/api/v1/receipts").file(file))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("receipt_id").asText();

        final JsonNode processed = process(receiptId);
        assertThat(processed.get("itemize_status").asText()).isEqualTo("FAILED");

        final JsonNode reitemized = itemize(processed.get("transaction_id").asText());

        assertThat(reitemized.get("itemize_status").asText())
                .as("a transaction with no usable header must not become COMPLETE")
                .isEqualTo("FAILED");
    }

    @Test
    void should_updateItems_withSuppliedItemId_preserveThatItemsIdentity() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();
        final JsonNode original = processed.get("line_items");
        final String espressoId = original.get(0).get("item_id").asText();
        final String sandwichId = original.get(1).get("item_id").asText();
        final String waterId = original.get(2).get("item_id").asText();

        // An edit: same three items, one amount changed, every id supplied.
        final String body = """
                { "items": [
                  { "item_id": "%s", "description": "Espresso", "amount": 3.00 },
                  { "item_id": "%s", "description": "Sandwich", "amount": 9.40 },
                  { "item_id": "%s", "description": "Mineral water", "amount": 2.60 }
                ] }
                """.formatted(espressoId, sandwichId, waterId);

        final String responseBody = patchItems(transactionId, body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        final JsonNode updated = objectMapper.readTree(responseBody).get("line_items");

        assertThat(updated).hasSize(3);
        assertThat(updated.get(0).get("item_id").asText())
                .as("editing an item must not destroy and recreate it")
                .isEqualTo(espressoId);
        assertThat(updated.get(1).get("item_id").asText()).isEqualTo(sandwichId);
        assertThat(updated.get(2).get("item_id").asText()).isEqualTo(waterId);
        assertThat(new BigDecimal(updated.get(1).get("amount").asText())).isEqualByComparingTo("9.40");
    }

    @Test
    void should_updateItems_withoutItemId_mintNewIdentity() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();
        final List<String> originalIds = idsOf(processed.get("line_items"));

        // A merge: the product is not any of the originals, so it gets a new id.
        final String responseBody = patchItems(transactionId,
                        "{\"items\":[{\"description\":\"Combined meal\",\"amount\":15.00}]}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        final JsonNode merged = objectMapper.readTree(responseBody).get("line_items");

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).get("item_id").asText()).isNotIn(originalIds);
    }

    @Test
    void should_updateItems_withUnknownItemId_returnBadRequestAndWriteNothing() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();
        final List<String> originalIds = idsOf(processed.get("line_items"));

        // Amounts reconcile, so a 409 cannot be the reason: the bad id must be reported on its own terms.
        patchItems(transactionId, "{\"items\":[{\"item_id\":\"does-not-exist\",\"description\":\"X\","
                        + "\"amount\":15.00}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-003"));

        assertThat(idsOf(getTransaction(transactionId).get("line_items")))
                .as("a rejected override must leave the stored items untouched")
                .isEqualTo(originalIds);
    }

    @Test
    void should_updateItems_withItemIdFromAnotherTransaction_returnBadRequest() throws Exception {
        final JsonNode first = processFixture("receipt-clean");
        final JsonNode second = processFixture("receipt-clean");
        final String foreignId = first.get("line_items").get(0).get("item_id").asText();

        // Patching the wrong transaction is a client bug; minting a new id would hide it.
        patchItems(second.get("transaction_id").asText(),
                        "{\"items\":[{\"item_id\":\"" + foreignId + "\",\"description\":\"Espresso\","
                                + "\"amount\":15.00}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-003"));
    }

    @Test
    void should_updateItems_withSameItemIdTwice_returnBadRequest() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String itemId = processed.get("line_items").get(0).get("item_id").asText();

        // Two rows claiming one identity would silently collapse a split into an edit.
        patchItems(processed.get("transaction_id").asText(),
                        "{\"items\":[{\"item_id\":\"" + itemId + "\",\"description\":\"A\",\"amount\":7.50},"
                                + "{\"item_id\":\"" + itemId + "\",\"description\":\"B\",\"amount\":7.50}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-003"));
    }

    @Test
    void should_updateItems_withSplitKeepingOneId_preserveThatIdAndMintTheRest() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();
        final String espressoId = processed.get("line_items").get(0).get("item_id").asText();
        final List<String> originalIds = idsOf(processed.get("line_items"));

        // Split the 15.00 net into four rows, keeping the espresso's identity.
        final String body = """
                { "items": [
                  { "item_id": "%s", "description": "Espresso", "amount": 3.50 },
                  { "description": "Sandwich half A", "amount": 4.45 },
                  { "description": "Sandwich half B", "amount": 4.45 },
                  { "description": "Mineral water", "amount": 2.60 }
                ] }
                """.formatted(espressoId);

        final String responseBody = patchItems(transactionId, body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        final JsonNode split = objectMapper.readTree(responseBody).get("line_items");

        assertThat(split).hasSize(4);
        assertThat(split.get(0).get("item_id").asText()).isEqualTo(espressoId);
        assertThat(idsOf(split).subList(1, 4)).doesNotContainAnyElementsOf(originalIds);
    }

    private List<String> idsOf(final JsonNode lineItems) {
        final List<String> ids = new ArrayList<>();
        lineItems.forEach(item -> ids.add(item.get("item_id").asText()));
        return ids;
    }

    @Test
    void should_getTransaction_withUnknownId_returnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXP-NOT-002"));
    }

    @Test
    void should_process_withUnknownReceiptId_returnNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/receipts/{id}/process", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXP-NOT-001"));
    }

    @Test
    void should_upload_withEmptyFile_returnBadRequest() throws Exception {
        final MockMultipartFile empty = new MockMultipartFile("file", "empty.txt",
                MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/receipts").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXP-VAL-001"));
    }

    @Test
    void should_process_withUnparseableText_returnFailedStatus() throws Exception {
        final MockMultipartFile noise = new MockMultipartFile("file", "scanner-noise.txt",
                MediaType.TEXT_PLAIN_VALUE, "just some noise with no receipt labels".getBytes());

        final String uploadBody = mockMvc.perform(multipart("/api/v1/receipts").file(noise))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        final String receiptId = objectMapper.readTree(uploadBody).get("receipt_id").asText();

        assertThat(process(receiptId).get("itemize_status").asText()).isEqualTo("FAILED");
    }

    @Test
    void should_getTransaction_neverExposeRawOcrText() throws Exception {
        final JsonNode processed = processFixture("receipt-clean");
        final String transactionId = processed.get("transaction_id").asText();

        // Raw OCR is stored, not published: no field, and no query parameter revives it.
        assertThat(processed.has("raw_ocr_text")).isFalse();
        assertThat(getTransaction(transactionId).has("raw_ocr_text")).isFalse();

        final String withLegacyParam = mockMvc.perform(
                        get("/api/v1/transactions/{id}?include=raw_ocr", transactionId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(withLegacyParam).has("raw_ocr_text")).isFalse();
        assertThat(withLegacyParam).doesNotContain("Cafe Mitte\nDATE");
    }

    @Test
    void should_getHealthLive_whenRunning_returnUp() throws Exception {
        mockMvc.perform(get("/health/live")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void should_getHealthReady_withPrerequisitesUp_returnUpWithDependencyDetail() throws Exception {
        mockMvc.perform(get("/health/ready")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("expense-capture"))
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.uptime_seconds").exists())
                .andExpect(jsonPath("$.runtime.java_version").exists())
                .andExpect(jsonPath("$.dependencies.storage.detail.writable").value(true))
                .andExpect(jsonPath("$.dependencies.ocr.detail.provider").value("stub"))
                // Only infrastructure prerequisites are probed, not ordinary application components.
                .andExpect(jsonPath("$.dependencies.repositories").doesNotExist());
    }

    @Test
    void should_exposeOnlyLiveAndReady_returnNotFoundForOtherHealthPaths() throws Exception {
        mockMvc.perform(get("/health/dependencies"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXP-NOT-003"));
    }
}
