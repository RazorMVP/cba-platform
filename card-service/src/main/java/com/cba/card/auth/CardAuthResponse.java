package com.cba.card.auth;

import java.math.BigDecimal;

/**
 * Authorization response DTO returned to fep-service.
 * Maps to fep-service AuthorizationResult record fields.
 */
public record CardAuthResponse(
        String     responseCode,
        String     authorizationCode,
        boolean    approved,
        boolean    standIn,            // true when decision made via stand-in (offline approval)
        BigDecimal availableBalance,   // populated for balance inquiries (processingCode 310000)
        String     currencyCode,
        String     mipReference        // Mastercard MIP reference; null for other schemes
) {
    public static CardAuthResponse approve(String authCode, BigDecimal balance, String currency) {
        return new CardAuthResponse("00", authCode, true, false, balance, currency, null);
    }

    public static CardAuthResponse decline(String responseCode) {
        return new CardAuthResponse(responseCode, null, false, false, null, null, null);
    }

    public static CardAuthResponse systemError() {
        return new CardAuthResponse("96", null, false, false, null, null, null);
    }
}
