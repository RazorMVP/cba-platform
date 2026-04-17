package com.cba.account.dto;

import com.cba.account.AccountStatus;
import com.cba.account.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        UUID customerId,
        String customerName,
        String productName,
        AccountType accountType,
        AccountStatus status,
        BigDecimal balance,
        /** balance minus sum of all active holds — the amount the account holder can actually spend. */
        BigDecimal availableBalance,
        /** sum of all active holds on this account. */
        BigDecimal onHoldAmount,
        String currencyCode,
        LocalDate openedDate,
        LocalDate closedDate,
        LocalDate lastTransactionDate,
        Instant createdAt
) {}
