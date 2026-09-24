package com.harness.expensecapture.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.harness.expensecapture.model.domain.Receipt;
import com.harness.expensecapture.repository.TransactionRepository;
import com.harness.expensecapture.service.ReceiptProcessingService;
import com.harness.expensecapture.service.ReceiptService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

/**
 * The one-transaction-per-receipt rule under concurrency.
 *
 * <p>Every HTTP request is served on its own thread, so {@code process} for one receipt can genuinely run in
 * parallel. A sequential test cannot catch the check-then-act race that a naive
 * {@code findByReceiptId(...).orElseGet(create)} introduces.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.upload-dir=./target/test-uploads",
        "app.ocr.fixture-dir=./fixtures/task-a"
})
@DisplayName("One transaction per receipt, under concurrency")
class ConcurrentProcessingIT {

    private static final int THREADS = 16;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private ReceiptProcessingService processingService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void should_process_calledConcurrentlyForOneReceipt_createExactlyOneTransaction() throws Exception {
        final Receipt receipt = uploadCleanReceipt();
        final long before = transactionRepository.count();

        final Set<String> transactionIds = Collections.synchronizedSet(new HashSet<>());
        runConcurrently(() -> transactionIds.add(processingService.process(receipt.id()).getId()));

        assertThat(transactionIds)
                .as("all concurrent callers must converge on one transaction id")
                .hasSize(1);
        assertThat(transactionRepository.count() - before)
                .as("exactly one transaction stored for one receipt")
                .isEqualTo(1L);
        assertThat(transactionRepository.findByReceiptId(receipt.id()))
                .as("the receiptId index must resolve to that same transaction")
                .isPresent()
                .get()
                .extracting(transaction -> transaction.getId())
                .isEqualTo(transactionIds.iterator().next());
    }

    @Test
    void should_process_calledConcurrently_stillMatchGoldTotals() throws Exception {
        final Receipt receipt = uploadCleanReceipt();

        runConcurrently(() -> processingService.process(receipt.id()));

        // Concurrency must not corrupt the aggregate: duplicated taxes or items would show up here.
        final var transaction = transactionRepository.findByReceiptId(receipt.id()).orElseThrow();
        assertThat(transaction.getTaxes()).hasSize(1);
        assertThat(transaction.getLineItems()).hasSize(3);
        assertThat(transaction.getGrandTotal().amount()).isEqualByComparingTo("17.85");
    }

    private Receipt uploadCleanReceipt() throws Exception {
        final byte[] content = Files.readAllBytes(Path.of("fixtures", "task-a", "receipt-clean.txt"));
        return receiptService.upload(
                new MockMultipartFile("file", "receipt-clean.txt", MediaType.TEXT_PLAIN_VALUE, content));
    }

    /** Releases all threads at once so they collide, rather than starting them one at a time. */
    private void runConcurrently(final Runnable action) throws InterruptedException {
        final CountDownLatch startGun = new CountDownLatch(1);
        final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    startGun.await();
                    action.run();
                    return null;
                });
            }
            startGun.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
