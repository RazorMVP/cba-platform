package com.cba.openbanking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsentRequest(

        /**
         * Optional. The consent always belongs to the calling partner organisation (the API key's);
         * a different value is rejected with {@code 400 TPP_CLIENT_MISMATCH}.
         */
        String tppClientId,

        /** Customer the TPP is requesting access for. Only this customer can authorise the consent. */
        @NotNull
        UUID customerId,

        /** Scopes from {@code ConsentScope}, e.g. ["accounts_read", "balances_read", "payments"]; others are 400 INVALID_SCOPE */
        @NotEmpty
        List<String> scopes,

        @Future
        Instant expiryDate
) {}
