package com.cba.fep.auth;

import com.cba.fep.emv.EmvData;
import com.cba.fep.scheme.SchemeType;
import lombok.Builder;
import lombok.With;

import java.util.Map;

/**
 * Authorization request DTO forwarded from the FEP to card-service.
 *
 * <p>Built by {@link com.cba.fep.router.AuthorizationHandler} from the
 * incoming ISO 8583 message. Contains all data needed by card-service to
 * make an authorization decision.
 */
@Builder
@With
public record AuthorizationRequest(
        // --- Card data ---
        String    pan,
        String    processingCode,
        String    amount,
        String    currencyCode,

        // --- Transaction identifiers ---
        String    stan,
        String    rrn,

        // --- Terminal / merchant ---
        String    terminalId,
        String    merchantId,
        String    merchantName,
        String    mcc,
        String    posEntryMode,
        String    posConditionCode,

        // --- Scheme ---
        SchemeType scheme,

        // --- Security ---
        boolean   pinVerified,
        boolean   arqcValid,
        EmvData   emvData,

        // --- Flow flags ---
        boolean   isFinancial,      // true = 0200 (capture with authorization)
        boolean   isSingleMessage,  // true = 0120 advice (already completed at terminal)

        // --- Scheme-specific private data (populated by SchemeAdapter) ---
        Map<String, String> schemeData
) {
    // Compact canonical constructor — provide defaults for optional fields
    public AuthorizationRequest {
        scheme    = scheme    != null ? scheme    : SchemeType.UNKNOWN;
        schemeData = schemeData != null ? schemeData : Map.of();
    }
}
