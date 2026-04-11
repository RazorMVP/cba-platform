package com.cba.fep.auth;

import java.math.BigDecimal;

/**
 * Authorization result returned by card-service to the FEP.
 *
 * <p>The FEP uses this to build the ISO 8583 0110 response message:
 * <ul>
 *   <li>{@link #responseCode} → DE39</li>
 *   <li>{@link #authorizationCode} → DE38 (only if approved)</li>
 *   <li>{@link #availableBalance} → DE54 (for balance inquiry responses)</li>
 *   <li>{@link #mipReference} → Mastercard DE111 (MIP reference number)</li>
 *   <li>{@link #standIn} → used to set Visa DE63 STIP flag</li>
 * </ul>
 */
public record AuthorizationResult(
        String     responseCode,
        String     authorizationCode,    // 6-char alphanumeric; null if declined
        boolean    approved,
        boolean    standIn,              // true = issuer was unreachable; stand-in decision
        BigDecimal availableBalance,     // null except for balance inquiry (proc code 31xxxx)
        String     currencyCode,         // ISO 4217 currency of the available balance
        String     mipReference          // Mastercard MIP routing reference; null for other schemes
) {
    /** Convenience factory for an approval result. */
    public static AuthorizationResult approve(String authCode) {
        return new AuthorizationResult("00", authCode, true, false, null, null, null);
    }

    /** Convenience factory for a decline result. */
    public static AuthorizationResult decline(String responseCode) {
        return new AuthorizationResult(responseCode, null, false, false, null, null, null);
    }

    /** System malfunction result when card-service is unreachable. */
    public static AuthorizationResult systemError() {
        return new AuthorizationResult("96", null, false, false, null, null, null);
    }
}
