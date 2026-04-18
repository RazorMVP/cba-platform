package com.cba.treasury;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TreasuryPlacementRequest(
        @NotBlank String reference,
        @NotBlank String counterpartyName,
        String counterpartyBic,
        @NotBlank String placementType,
        @NotNull @DecimalMin("0.01") BigDecimal principal,
        @NotNull BigDecimal interestRate,
        String currencyCode,
        @NotNull LocalDate startDate,
        @NotNull LocalDate maturityDate,
        BigDecimal expectedReturn,
        UUID glSourceAccount,
        UUID glIncomeAccount,
        String notes
) {}
