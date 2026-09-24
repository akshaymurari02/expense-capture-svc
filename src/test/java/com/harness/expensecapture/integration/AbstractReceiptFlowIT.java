package com.harness.expensecapture.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Shared plumbing for the end-to-end fixture tests. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.upload-dir=./target/test-uploads",
        "app.ocr.fixture-dir=./fixtures/task-a"
})
abstract class AbstractReceiptFlowIT {

    protected static final Path FIXTURES = Path.of("fixtures", "task-a");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected JsonNode gold;

    @BeforeEach
    void loadGold() throws Exception {
        gold = objectMapper.readTree(Files.readString(FIXTURES.resolve("gold.json"), StandardCharsets.UTF_8));
    }

    /** Uploads the named fixture as if it were a scanned receipt and returns the new receipt id. */
    protected String uploadFixture(final String fixtureName) throws Exception {
        final byte[] content = Files.readAllBytes(FIXTURES.resolve(fixtureName + ".txt"));
        final MockMultipartFile file = new MockMultipartFile("file", fixtureName + ".txt",
                MediaType.TEXT_PLAIN_VALUE, content);

        final String body = mockMvc.perform(multipart("/api/v1/receipts").file(file))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("receipt_id").asText();
    }

    protected JsonNode process(final String receiptId) throws Exception {
        final String body = mockMvc.perform(post("/api/v1/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode getTransaction(final String transactionId) throws Exception {
        final String body = mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected JsonNode processFixture(final String fixtureName) throws Exception {
        return process(uploadFixture(fixtureName));
    }

    protected JsonNode itemize(final String transactionId) throws Exception {
        final String body = mockMvc.perform(post("/api/v1/transactions/{id}/itemize", transactionId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    protected org.springframework.test.web.servlet.ResultActions patchItems(final String transactionId,
            final String jsonBody) throws Exception {
        return mockMvc.perform(patch("/api/v1/transactions/{id}/items", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody));
    }
}
