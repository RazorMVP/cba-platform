package com.cba.currency.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ExchangeRateRequest(
        @NotBlank @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
        String fromCurrency,

        @NotBlank @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
        String toCurrency,

        @NotNull @DecimalMin(value = "0.00000001", message = "Rate must be positive")
        BigDecimal rate
) {}
