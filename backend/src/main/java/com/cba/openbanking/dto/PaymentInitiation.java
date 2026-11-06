package com.cba.openbanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The payment a {@code payments} consent allows (UK OB domestic-payment-consent "Initiation"):
 * exactly this amount, in this currency, to this creditor account, once. The customer authorises
 * this payment, not "payments" in general.
 */
public record PaymentInitiation(

        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4)
        BigDecimal instructedAmount,

        /** ISO 4217 alphabetic, e.g. "USD"; must be the debited account's currency. */
        @NotNull @Pattern(regexp = "[A-Za-z]{3}")
        String currency,

        @NotNull
        UUID creditorAccountId,

        /** Optional. When set, the payment must come from this account of the consent's customer. */
        UUID debtorAccountId,

        @Size(max = 140)
        String reference
) {}
