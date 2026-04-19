package com.cba.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ExternalPaymentRequest(

        @NotNull UUID sourceAccountId,

        @NotNull @Positive BigDecimal amount,

        @NotBlank String currencyCode,

        /** SWIFT | SEPA | ACH */
        @NotBlank String network,

        @NotBlank String beneficiaryName,

        /** IBAN — required for SEPA; optional for SWIFT when accountNumber is provided */
        String beneficiaryIban,

        /** BIC/SWIFT code of the beneficiary's bank — required for SWIFT */
        String beneficiaryBic,

        String beneficiaryBankName,

        /** ISO 3166-1 alpha-3 country code */
        String beneficiaryCountryCode,

        /** SWIFT charge bearer: SHA | OUR | BEN (defaults to SHA) */
        String chargeType,

        String description,

        /** Caller-supplied external reference (e.g. invoice number) */
        String externalReference
) {}
