package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;

public record WaiveInterestRequest(
        @NotBlank String reason
) {}
