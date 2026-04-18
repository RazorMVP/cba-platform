package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ForecloseRequest(
        LocalDate foreclosureDate,
        @NotBlank String reason
) {}
