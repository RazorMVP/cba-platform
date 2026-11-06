package com.cba.openbanking.dto;

import com.cba.openbanking.ConsentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A consent as bank staff see it ({@code /api/v1/open-banking/consents}): with the customer's and the
 * TPP's names, and the kind of access it gives — {@code PISP} (payments), {@code CBPII} (funds
 * confirmation) or {@code AISP} (account information).
 */
public record StaffConsentResponse(
        String consentId,
        String consentType,
        UUID customerId,
        String customerName,
        String tppClientId,
        String tppName,
        List<String> scopes,
        ConsentStatus status,
        Instant expiryDate,
        Instant createdAt,
        Instant updatedAt,
        PaymentInitiation initiation,
        UUID paymentId,
        Instant consumedAt
) {}
