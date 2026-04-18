package com.cba.treasury;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TreasuryInterbankRequest(
        @NotBlank String reference,
        @NotBlank String counterpartyName,
        String counterpartyBic,
        @NotBlank String direction,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currencyCode,
        @NotNull BigDecimal interestRate,
        @NotNull LocalDate startDate,
        LocalDate maturityDate,
        UUID settlementGl,
        String notes
) {}
