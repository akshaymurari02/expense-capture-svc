package com.harness.expensecapture.controller;

import com.harness.expensecapture.model.dto.TransactionResponse;
import com.harness.expensecapture.model.dto.UploadResponse;
import com.harness.expensecapture.service.ReceiptProcessingService;
import com.harness.expensecapture.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Receipt upload and processing endpoints. */
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptProcessingService processingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") final MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UploadResponse.from(receiptService.upload(file)));
    }

    @PostMapping("/{receiptId}/process")
    public ResponseEntity<TransactionResponse> process(@PathVariable final String receiptId) {
        return ResponseEntity.ok(TransactionResponse.from(processingService.process(receiptId)));
    }
}
