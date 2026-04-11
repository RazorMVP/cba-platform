package com.cba.card.threeds;

/**
 * Response to {@code POST /3ds/acs/challenge/{acsTransId}/verify}.
 *
 * <p>Returned as JSON when the caller is a REST client (mobile app or
 * 3DS SDK). For browser-based flows the controller renders HTML instead.
 */
public record ChallengeVerifyResponse(

        /** "AUTHENTICATED", "FAILED", or "LOCKED" (max attempts exceeded). */
        String status,

        /**
         * ECI indicator present on successful authentication.
         * "05" = fully authenticated via challenge.
         */
        String eci,

        /**
         * Base64-encoded CAVV — present only when status = "AUTHENTICATED".
         * The 3DS Server/merchant must include this in DE 55 of the ISO 8583
         * authorization request to claim the liability shift.
         */
        String authenticationValue,

        /** Human-readable message for display. */
        String message
) {
    static ChallengeVerifyResponse authenticated(String eci, String cavv) {
        return new ChallengeVerifyResponse("AUTHENTICATED", eci, cavv,
                "Authentication successful");
    }

    static ChallengeVerifyResponse failed(int attemptsRemaining) {
        return new ChallengeVerifyResponse("FAILED", null, null,
                "Incorrect OTP. Attempts remaining: " + attemptsRemaining);
    }

    static ChallengeVerifyResponse locked() {
        return new ChallengeVerifyResponse("LOCKED", null, null,
                "Maximum OTP attempts exceeded. Session locked.");
    }
}
