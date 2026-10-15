package com.cba.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A card authorization's reservation (card-service).
 *
 * @param reference    card-service's idempotency key — the same reference never holds twice
 * @param amount       major units of {@code currencyCode}
 * @param currencyCode ISO 4217 alphabetic; must match the account's
 */
public record CardHoldRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 3) String currencyCode
) {}
