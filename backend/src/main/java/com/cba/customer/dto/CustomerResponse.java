package com.cba.customer.dto;

import com.cba.customer.KycStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String externalId,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        KycStatus kycStatus,
        // Lifecycle dates
        LocalDate activationDate,
        LocalDate closureDate,
        LocalDate rejectionDate,
        LocalDate withdrawalDate,
        // Lifecycle reasons
        String closureReason,
        String rejectionReason,
        String withdrawalReason,
        // Staff / office
        UUID staffId,
        UUID officeId,
        // Transfer
        UUID transferToOfficeId,
        LocalDate transferDate,
        String transferNote,
        // Timestamps
        Instant createdAt,
        Instant updatedAt
) {}
