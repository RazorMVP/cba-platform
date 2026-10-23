package com.cba.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Optional body of a card hold release (card-service reversal). Both fields may be omitted.
 *
 * @param amount       the reversal amount, major units. For a completed debit (CAPTURED) only this much
 *                     is refunded — a partial reversal never refunds the whole debit; more than was
 *                     captured is refused. For a hold that was never seen, it is recorded as released so
 *                     a debit arriving late (after card-service gave up waiting) is refused.
 * @param currencyCode ISO 4217 alphabetic; must match the account's when given
 */
public record CardReleaseRequest(
        @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 3) String currencyCode
) {}
