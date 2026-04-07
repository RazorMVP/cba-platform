package com.cba.office.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record OfficeRequest(
        @NotBlank String name,
        @NotBlank String externalId,
        LocalDate openingDate,
        UUID parentId,
        String description
) {}
