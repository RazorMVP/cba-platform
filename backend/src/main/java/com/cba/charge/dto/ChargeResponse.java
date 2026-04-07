package com.cba.charge.dto;

import com.cba.charge.ChargeDefinition;
import java.math.BigDecimal;
import java.util.UUID;

public record ChargeResponse(
        UUID id,
        String name,
        String currencyCode,
        ChargeDefinition.ChargeAppliesTo chargeAppliesTo,
        ChargeDefinition.ChargeTimeType chargeTimeType,
        ChargeDefinition.ChargeCalculation chargeCalculation,
        BigDecimal amount,
        boolean active,
        boolean penalty
) {
    public static ChargeResponse from(ChargeDefinition c) {
        return new ChargeResponse(
                c.getId(), c.getName(), c.getCurrencyCode(),
                c.getChargeAppliesTo(), c.getChargeTimeType(), c.getChargeCalculation(),
                c.getAmount(), c.isActive(), c.isPenalty()
        );
    }
}
