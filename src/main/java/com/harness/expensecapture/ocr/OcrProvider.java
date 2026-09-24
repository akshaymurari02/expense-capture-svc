package com.harness.expensecapture.ocr;

import com.harness.expensecapture.model.domain.Receipt;

/**
 * Port for turning a stored receipt file into raw text.
 *
 * <p>This is the abstraction that varies by <em>file type</em>: PDF, PNG and JPEG differ in how text is
 * obtained from them but all yield a {@code String}. Supporting a new format therefore means adding an
 * implementation here, leaving {@code ReceiptTextParser} untouched — which is why parsing is deliberately
 * <em>not</em> folded into this interface.
 *
 * <p>Implementations are selected by {@link #providerName()} against {@code app.ocr.provider} in
 * {@code OcrProviderConfig}, so a vendor-backed provider (Vision, Textract, a VLM) is added by writing a class
 * and setting one property. No service changes.
 */
public interface OcrProvider {

    /**
     * @return raw text for the receipt; never {@code null} or blank
     */
    String extractText(Receipt receipt);

    /** Identifier used to select this provider via {@code app.ocr.provider}; must be unique and stable. */
    String providerName();
}
