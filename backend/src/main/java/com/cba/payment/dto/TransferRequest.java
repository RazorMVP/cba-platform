package com.cba.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,

        @NotNull @DecimalMin("0.01")
        BigDecimal amount,

        String description,

        /**
         * Optional external destination account number string.
         * When provided, validated against the tenant's configured account
         * number algorithm before the transfer is processed.
         * Null for internal (UUID-based) transfers.
         */
        String destinationAccountNumber
) {}
