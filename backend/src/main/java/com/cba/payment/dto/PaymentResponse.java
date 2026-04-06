package com.cba.payment.dto;

import com.cba.payment.PaymentStatus;
import com.cba.payment.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        String referenceNumber,
        PaymentType paymentType,
        UUID sourceAccountId,
        String sourceAccountNumber,
        UUID destinationAccountId,
        String destinationAccountNumber,
        BigDecimal amount,
        String currencyCode,
        String description,
        PaymentStatus status,
        Instant executedDate,
        Instant createdAt,

        // Cross-currency audit fields (null for same-currency transfers)
        boolean crossCurrency,
        String sourceCurrency,
        BigDecimal sourceAmount,
        String destinationCurrency,
        BigDecimal destinationAmount,
        BigDecimal exchangeRateUsed
) {}
