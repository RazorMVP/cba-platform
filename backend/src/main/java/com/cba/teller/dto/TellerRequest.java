package com.cba.teller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TellerRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotBlank @Size(max = 10) String branchCode,
        String officeId,
        LocalDate startDate,
        LocalDate endDate
) {}
