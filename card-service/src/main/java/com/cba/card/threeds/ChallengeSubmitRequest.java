package com.cba.card.threeds;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cardholder OTP submission from the challenge form.
 *
 * <p>Received as a JSON body on {@code POST /3ds/acs/challenge/{acsTransId}/verify}.
 * Also supports HTML form submission (Spring MVC will bind either).
 */
public record ChallengeSubmitRequest(

        /** The one-time password entered by the cardholder. 4–8 digits. */
        @NotBlank
        @Size(min = 4, max = 8)
        String otp
) {}
