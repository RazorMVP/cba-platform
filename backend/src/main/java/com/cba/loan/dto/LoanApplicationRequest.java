package com.cba.loan.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record LoanApplicationRequest(
        @NotNull UUID customerId,
        @NotNull UUID productId,
        @NotNull UUID linkedAccountId,

        @NotNull @DecimalMin("1.00")
        BigDecimal principalAmount,

        @NotNull @Min(1)
        Integer termMonths,

        String notes
) {}
