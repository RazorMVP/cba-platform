package com.cba.currency.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ExchangeRateResponse(
        UUID id,
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        /** Convenience: inverse rate (1 toCurrency = ? fromCurrency) */
        BigDecimal inverseRate,
        boolean active,
        Instant updatedAt
) {}
