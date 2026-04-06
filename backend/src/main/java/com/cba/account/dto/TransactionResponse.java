package com.cba.account.dto;

import com.cba.account.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal runningBalance,
        String currencyCode,
        String description,
        String referenceNumber,
        Instant transactionDate,
        LocalDate valueDate
) {}
