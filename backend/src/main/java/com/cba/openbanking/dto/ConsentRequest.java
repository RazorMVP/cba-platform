package com.cba.openbanking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConsentRequest(

        @NotBlank
        String tppClientId,

        /** Customer the TPP is requesting access for. Required at consent creation. */
        UUID customerId,

        /** Scopes requested e.g. ["accounts", "transactions", "payments"] */
        @NotEmpty
        List<String> scopes,

        @Future
        Instant expiryDate
) {}
