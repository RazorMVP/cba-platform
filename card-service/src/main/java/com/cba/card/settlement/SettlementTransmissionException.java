package com.cba.card.settlement;

/**
 * Thrown when a settlement file transmission fails.
 * Retryable — the {@link SettlementFileExportService} will retry up to
 * {@code card.settlement.export.max-retries} times before marking FAILED.
 */
public class SettlementTransmissionException extends RuntimeException {
    public SettlementTransmissionException(String message) {
        super(message);
    }
    public SettlementTransmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
