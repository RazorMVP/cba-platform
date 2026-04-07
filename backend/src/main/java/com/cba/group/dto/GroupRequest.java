package com.cba.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record GroupRequest(
        @NotBlank String name,
        String externalId,
        @NotNull UUID officeId,
        UUID staffId,
        UUID centerId,
        LocalDate activationDate
) {}
