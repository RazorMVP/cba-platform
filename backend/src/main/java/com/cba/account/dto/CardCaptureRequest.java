package com.cba.account.dto;

import com.cba.account.CardCharges;
import com.cba.account.CardInterchangeDirection;

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
 * @param interchangeAmount    interchange on this transaction, major units (optional; backlog 15b)
 * @param interchangeDirection EARNED (purchase) or PAID (ATM cash); required when interchange is above zero
 * @param schemeFeeAmount      card scheme fees on this transaction, major units (optional)
 * @param scheme               card scheme (VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY), recorded on the hold
 */
public record CardCaptureRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 3) String currencyCode,
        BigDecimal interchangeAmount,
        CardInterchangeDirection interchangeDirection,
        BigDecimal schemeFeeAmount,
        @Size(max = 20) String scheme
) {
    /** A capture without interchange or fees (posts as in 15a). */
    public CardCaptureRequest(BigDecimal amount, String currencyCode) {
        this(amount, currencyCode, null, null, null, null);
    }

    public CardCharges charges() {
        return new CardCharges(interchangeAmount, interchangeDirection, schemeFeeAmount);
    }
}
