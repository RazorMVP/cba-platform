package com.cba.currency.dto;

import java.math.BigDecimal;

/**
 * Result of a currency conversion calculation.
 * Used internally by PaymentService when processing cross-currency transfers.
 */
public record ConversionResult(
        String fromCurrency,
        String toCurrency,
        BigDecimal sourceAmount,
        BigDecimal convertedAmount,
        BigDecimal rateUsed
) {}
