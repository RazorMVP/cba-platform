package com.cba.teller.dto;

import com.cba.teller.CashTransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CashTransactionRequest(
        @NotNull CashTransactionType transactionType,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currencyCode,
        /** Customer account to credit (CASH_IN) or debit (CASH_OUT) */
        UUID accountId,
        String description
) {}
