package com.cba.cardprepaid.dto;

import java.math.BigDecimal;

/**
 * The backend's answer to a prepaid movement. {@code reason} is set when {@code approved} is false; a
 * repeat of the same request returns the first posting with {@code repeat=true}. The figures are what was
 * posted (clearing) or reversed (refund), major units.
 */
public record PrepaidMovementResult(
        boolean approved,
        boolean repeat,
        String reference,
        String reason,
        String journalTransactionId,
        BigDecimal interchangeAmount,
        String interchangeDirection,
        BigDecimal schemeFeeAmount,
        BigDecimal interchangeReversed,
        BigDecimal schemeFeeReversed) {

    public static PrepaidMovementResult declined(String reference, String reason) {
        return new PrepaidMovementResult(false, false, reference, reason, null, null, null, null, null, null);
    }
}
