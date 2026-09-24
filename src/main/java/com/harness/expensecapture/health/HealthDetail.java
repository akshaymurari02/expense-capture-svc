package com.harness.expensecapture.health;

/**
 * Typed detail payload for a dependency check. Sealed rather than an untyped map so each dependency's
 * diagnostics have a real contract.
 */
public sealed interface HealthDetail {

    /** Upload directory diagnostics. */
    record StorageDetail(String uploadDir, boolean exists, boolean writable, long freeSpaceMb)
            implements HealthDetail {
    }

    /** OCR provider diagnostics. */
    record OcrDetail(String provider, String fixtureDir, boolean readable) implements HealthDetail {
    }
}
