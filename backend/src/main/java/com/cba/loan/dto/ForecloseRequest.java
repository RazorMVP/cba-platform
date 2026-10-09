package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Settles a loan early. The borrower pays the foreclosure quote for {@code foreclosureDate}
 * from {@code paymentMethod}: ACCOUNT (default — linked account or {@code sourceAccountId})
 * or CASH at {@code tellerSessionId}.
 */
public record ForecloseRequest(
        LocalDate foreclosureDate,
        @NotBlank String reason,
        String paymentMethod,
        UUID sourceAccountId,
        UUID tellerSessionId
) {
    public ForecloseRequest(LocalDate foreclosureDate, String reason) {
        this(foreclosureDate, reason, null, null, null);
    }
}
