package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Writes a credit-impaired loan off (IFRS 9 §5.4.4). {@code approvalReference} records the
 * board (or delegated authority) approval CBN requires; the service rejects a write-off
 * without it.
 */
public record WriteOffRequest(
        LocalDate writeOffDate,
        @NotBlank String reason,
        @Size(max = 100) String approvalReference
) {
    public WriteOffRequest(LocalDate writeOffDate, String reason) {
        this(writeOffDate, reason, null);
    }
}
