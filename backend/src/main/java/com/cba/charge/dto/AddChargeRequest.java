package com.cba.charge.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AddChargeRequest(
        @NotNull UUID chargeDefinitionId,
        @DecimalMin("0.0") BigDecimal amount,
        LocalDate dueDate,
        Integer installmentNumber
) {}
