package com.cba.teller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OpenSessionRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingBalance,
        @Size(min = 3, max = 3) String currencyCode
) {}
