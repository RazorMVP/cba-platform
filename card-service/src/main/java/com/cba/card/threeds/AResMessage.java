package com.cba.card.threeds;

import java.util.UUID;

/**
 * Outbound 3DS 2.x Authentication Response (ARes) returned to the Directory Server.
 *
 * <p>The ACS builds this after deciding frictionless vs. challenge.
 *
 * <ul>
 *   <li>{@code transStatus = "Y"} — Frictionless: authentication successful.</li>
 *   <li>{@code transStatus = "N"} — Authentication failed / declined.</li>
 *   <li>{@code transStatus = "C"} — Challenge required; DS must redirect cardholder
 *       to {@code acsURL} to begin challenge flow.</li>
 *   <li>{@code transStatus = "A"} — Attempted; frictionless path but no full auth.
 *       ECI 06 liability shift may or may not apply depending on scheme rules.</li>
 * </ul>
 *
 * <p>JSON field names use EMVCo 3DS 2.3 naming conventions.
 */
public record AResMessage(

        /** Echoed from AReq. */
        UUID threeDSServerTransID,

        /** Echoed from AReq. */
        UUID dsTransID,

        /** Assigned by this ACS for the session. */
        UUID acsTransID,

        /**
         * Authentication result:
         * "Y" = authenticated, "N" = not authenticated, "C" = challenge, "A" = attempted.
         */
        String transStatus,

        /**
         * Electronic Commerce Indicator:
         * "05" = fully authenticated (challenge path), "06" = attempted / frictionless.
         * Null when transStatus = "C" or "N".
         */
        String eci,

        /**
         * Base64-encoded CAVV (Cardholder Authentication Verification Value).
         * Present only when {@code transStatus} is "Y" or "A".
         */
        String authenticationValue,

        /**
         * URL the DS should redirect the cardholder's browser to for challenge.
         * Present only when {@code transStatus} is "C".
         */
        String acsURL,

        /** Message version — this ACS implements EMVCo 3DS 2.3. */
        String messageVersion
) {
    static final String MSG_VERSION = "2.3.1";

    /** Factory: frictionless approval (transStatus = Y, ECI 05). */
    static AResMessage frictionless(UUID serverTransId, UUID dsTransId,
                                    UUID acsTransId, String cavv) {
        return new AResMessage(serverTransId, dsTransId, acsTransId,
                "Y", "05", cavv, null, MSG_VERSION);
    }

    /** Factory: challenge required (transStatus = C). No CAVV yet. */
    static AResMessage challenge(UUID serverTransId, UUID dsTransId,
                                 UUID acsTransId, String acsUrl) {
        return new AResMessage(serverTransId, dsTransId, acsTransId,
                "C", null, null, acsUrl, MSG_VERSION);
    }

    /** Factory: authentication declined (transStatus = N). */
    static AResMessage declined(UUID serverTransId, UUID dsTransId, UUID acsTransId) {
        return new AResMessage(serverTransId, dsTransId, acsTransId,
                "N", "07", null, null, MSG_VERSION);
    }

    /** Factory: attempted — frictionless but no full authentication (ECI 06). */
    static AResMessage attempted(UUID serverTransId, UUID dsTransId,
                                  UUID acsTransId, String cavv) {
        return new AResMessage(serverTransId, dsTransId, acsTransId,
                "A", "06", cavv, null, MSG_VERSION);
    }
}
