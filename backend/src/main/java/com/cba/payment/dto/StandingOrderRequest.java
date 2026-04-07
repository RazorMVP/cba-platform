package com.cba.payment.dto;

import com.cba.payment.StandingOrder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StandingOrderRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currencyCode,
        @NotNull StandingOrder.Frequency frequency,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String description
) {}
