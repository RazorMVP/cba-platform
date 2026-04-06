package com.cba.openbanking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DomesticPaymentResponse(
        String domesticPaymentId,
        String consentId,
        String status,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String reference,
        Instant creationDateTime
) {}
