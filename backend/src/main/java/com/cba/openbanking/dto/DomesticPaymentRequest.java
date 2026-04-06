package com.cba.openbanking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DomesticPaymentRequest(

        /** Consent ID authorising this payment initiation */
        @NotBlank
        String consentId,

        @NotNull
        UUID sourceAccountId,

        @NotNull
        UUID destinationAccountId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3)
        String currency,

        String reference,

        String unstructuredRemittance
) {}
