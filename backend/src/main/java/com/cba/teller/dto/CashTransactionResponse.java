package com.cba.teller.dto;

import com.cba.teller.CashTransaction;
import com.cba.teller.CashTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashTransactionResponse(
        UUID id,
        UUID sessionId,
        UUID tellerId,
        UUID cashierId,
        UUID accountId,
        /** Set for cash received against a loan (no deposit account involved). */
        UUID loanId,
        CashTransactionType transactionType,
        BigDecimal amount,
        String currencyCode,
        String description,
        String referenceNumber,
        Instant transactionDate
) {
    public static CashTransactionResponse from(CashTransaction t) {
        return new CashTransactionResponse(
                t.getId(),
                t.getSession().getId(),
                t.getTeller().getId(),
                t.getCashier().getId(),
                t.getAccount() != null ? t.getAccount().getId() : null,
                t.getLoanId(),
                t.getTransactionType(),
                t.getAmount(),
                t.getCurrencyCode(),
                t.getDescription(),
                t.getReferenceNumber(),
                t.getTransactionDate()
        );
    }
}
