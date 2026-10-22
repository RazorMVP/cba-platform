package com.cba.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Clearing of a card authorization (card-service): debit the account for the presented amount.
 *
 * @param amount       major units of {@code currencyCode}; at most the authorized (held) amount
 * @param currencyCode ISO 4217 alphabetic; must match the account's
 */
public record CardCaptureRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 3) String currencyCode
) {}
