package com.cba.office.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record StaffRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        String email,
        String mobileNo,
        LocalDate joiningDate,
        boolean loanOfficer,
        @NotNull UUID officeId
) {}
