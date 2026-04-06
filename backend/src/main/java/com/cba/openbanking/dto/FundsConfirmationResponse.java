package com.cba.openbanking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FundsConfirmationResponse(
        String fundsConfirmationId,
        String consentId,
        boolean fundsAvailable,
        UUID accountId,
        BigDecimal requestedAmount,
        String currency,
        Instant confirmedAt
) {}
