package com.cba.treasury;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record LiquidityReserveRequest(
        @NotBlank String currencyCode,
        @NotNull @DecimalMin("0") BigDecimal minimumBalance,
        @DecimalMin("0") @DecimalMax("100") BigDecimal minimumRatioPercent,
        @DecimalMin("0") @DecimalMax("100") BigDecimal alertThresholdPercent,
        String regulatoryReference
) {}
