package com.cba.customer.dto;

import com.cba.customer.KycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer response DTO.
 * PII fields (email, phone, nationalId) are included only for ADMIN/TELLER.
 * The controller masks these for CUSTOMER role.
 */
public record CustomerResponse(
        UUID id,
        String externalId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        KycStatus kycStatus,
        Instant createdAt,
        Instant updatedAt
) {}
