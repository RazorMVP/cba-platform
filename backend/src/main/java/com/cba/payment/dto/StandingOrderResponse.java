package com.cba.payment.dto;

import com.cba.payment.StandingOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StandingOrderResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currencyCode,
        StandingOrder.Frequency frequency,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextExecutionDate,
        String description,
        StandingOrder.Status status,
        Instant lastExecutedAt,
        Instant createdAt
) {
    public static StandingOrderResponse from(StandingOrder o) {
        return new StandingOrderResponse(
                o.getId(),
                o.getSourceAccount().getId(),
                o.getDestinationAccount().getId(),
                o.getAmount(), o.getCurrencyCode(),
                o.getFrequency(),
                o.getStartDate(), o.getEndDate(),
                o.getNextExecutionDate(),
                o.getDescription(), o.getStatus(),
                o.getLastExecutedAt(), o.getCreatedAt());
    }
}
