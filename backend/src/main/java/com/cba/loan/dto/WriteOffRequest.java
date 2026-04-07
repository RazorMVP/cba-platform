package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record WriteOffRequest(
        LocalDate writeOffDate,
        @NotBlank String reason
) {}
