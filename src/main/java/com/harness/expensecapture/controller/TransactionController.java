package com.harness.expensecapture.controller;

import com.harness.expensecapture.model.dto.TransactionResponse;
import com.harness.expensecapture.model.dto.UpdateItemsRequest;
import com.harness.expensecapture.service.ItemOverrideService;
import com.harness.expensecapture.service.ItemizeService;
import com.harness.expensecapture.service.TransactionLookup;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Transaction read, re-itemize and user-override endpoints. */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionLookup transactionLookup;
    private final ItemizeService itemizeService;
    private final ItemOverrideService itemOverrideService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> get(@PathVariable final String transactionId) {
        return ResponseEntity.ok(TransactionResponse.from(transactionLookup.requireById(transactionId)));
    }

    @PostMapping("/{transactionId}/itemize")
    public ResponseEntity<TransactionResponse> itemize(@PathVariable final String transactionId) {
        return ResponseEntity.ok(TransactionResponse.from(itemizeService.reitemize(transactionId)));
    }

    @PatchMapping(path = "/{transactionId}/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionResponse> updateItems(@PathVariable final String transactionId,
            @Valid @RequestBody final UpdateItemsRequest request) {
        return ResponseEntity.ok(
                TransactionResponse.from(itemOverrideService.replaceItems(transactionId, request)));
    }
}
