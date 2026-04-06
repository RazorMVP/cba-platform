package com.cba.teller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record CashierRequest(
        @NotBlank @Size(max = 100) String staffId,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        boolean fullDay,
        LocalTime startTime,
        LocalTime endTime
) {}
