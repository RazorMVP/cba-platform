package com.cba.card.interchange;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SchemeFeeRequest(
        @NotBlank String scheme,

        @NotNull SchemeFeeType feeType,

        @NotNull @DecimalMin("0.0") BigDecimal ratePercent,

        @NotNull @DecimalMin("0.0") BigDecimal fixedFee,

        @NotNull LocalDate effectiveFrom,

        /** Null = indefinitely active. */
        LocalDate effectiveTo,

        boolean active
) {}
