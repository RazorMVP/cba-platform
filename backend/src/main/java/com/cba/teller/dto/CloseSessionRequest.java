package com.cba.teller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CloseSessionRequest(
        /** Physical cash counted at settlement time */
        @NotNull @DecimalMin("0.00") BigDecimal actualCash,
        String settlementNote
) {}
