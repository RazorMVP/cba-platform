package com.cba.account.dto;

import com.cba.account.CardCharges;
import com.cba.account.CardInterchangeDirection;

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
 * @param interchangeAmount    interchange on this transaction, major units (optional; backlog 15b)
 * @param interchangeDirection EARNED (purchase) or PAID (ATM cash); required when interchange is above zero
 * @param schemeFeeAmount      card scheme fees on this transaction, major units (optional)
 * @param scheme               card scheme, recorded on the hold
 */
public record CardDebitRequest(
        @NotBlank @Size(max = 64) String reference,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 3) String currencyCode,
        boolean cashWithdrawal,
        BigDecimal interchangeAmount,
        CardInterchangeDirection interchangeDirection,
        BigDecimal schemeFeeAmount,
        @Size(max = 20) String scheme
) {
    /** A debit without interchange or fees (posts as in 15a). */
    public CardDebitRequest(String reference, BigDecimal amount, String currencyCode, boolean cashWithdrawal) {
        this(reference, amount, currencyCode, cashWithdrawal, null, null, null, null);
    }

    public CardCharges charges() {
        return new CardCharges(interchangeAmount, interchangeDirection, schemeFeeAmount);
    }
}
