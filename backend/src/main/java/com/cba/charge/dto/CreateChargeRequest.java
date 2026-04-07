package com.cba.charge.dto;

import com.cba.charge.ChargeDefinition;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateChargeRequest(
        @NotBlank String name,
        String currencyCode,
        @NotNull ChargeDefinition.ChargeAppliesTo chargeAppliesTo,
        @NotNull ChargeDefinition.ChargeTimeType chargeTimeType,
        ChargeDefinition.ChargeCalculation chargeCalculation,
        @NotNull @DecimalMin("0.0") BigDecimal amount,
        boolean active,
        boolean penalty,
        Integer feeFrequency,
        Integer feeInterval
) {}
