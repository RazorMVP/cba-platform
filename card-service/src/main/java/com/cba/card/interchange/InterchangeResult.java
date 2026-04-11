package com.cba.card.interchange;

import java.math.BigDecimal;

/**
 * Immutable result of an interchange calculation.
 *
 * <pre>
 * netSettlementAmount = grossAmount − interchangeAmount − schemeFeeAmount
 * </pre>
 */
public record InterchangeResult(
        BigDecimal interchangeAmount,
        BigDecimal schemeFeeAmount,
        BigDecimal netSettlementAmount,
        String rateApplied
) {
    /** Sentinel result when no rate is configured for this transaction. */
    public static InterchangeResult noRate(BigDecimal grossAmount) {
        return new InterchangeResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                grossAmount,
                "NO_RATE_CONFIGURED");
    }
}
