package com.cba.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A single-message card transaction (card-service, ISO 8583 0200 — e.g. ATM cash): approved and
 * completed at once, so the account is debited immediately instead of held.
 *
 * @param reference      card-service's idempotency key — the same reference never debits twice
 * @param amount         major units of {@code currencyCode}
 * @param currencyCode   ISO 4217 alphabetic; must match the account's
 * @param cashWithdrawal true for cash (processing code 01xxxx), false for a purchase
 */
public record CardDebitRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 3) String currencyCode,
        boolean cashWithdrawal
) {}
