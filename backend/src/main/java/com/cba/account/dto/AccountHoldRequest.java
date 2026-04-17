package com.cba.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountHoldRequest(
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Hold amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "Reason is required")
        String reason,

        /** Optional expiry — if omitted the hold never expires automatically. */
        LocalDate expiryDate
) {}
