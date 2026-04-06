package com.cba.openbanking.dto;

import com.cba.openbanking.ConsentStatus;
import com.cba.openbanking.OpenBankingConsent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsentResponse(
        UUID id,
        String consentId,
        UUID customerId,
        String tppClientId,
        List<String> scopes,
        ConsentStatus status,
        Instant expiryDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConsentResponse from(OpenBankingConsent c) {
        return new ConsentResponse(
                c.getId(),
                c.getConsentId(),
                c.getCustomer() != null ? c.getCustomer().getId() : null,
                c.getTppClientId(),
                c.getScopes(),
                c.getStatus(),
                c.getExpiryDate(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
