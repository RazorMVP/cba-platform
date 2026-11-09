package com.cba.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ops recording an external payment's outcome by hand: SETTLED or RETURNED, with a reason. */
public record ExternalPaymentOutcomeRequest(
        @NotBlank String outcome,
        @NotBlank @Size(max = 500) String reason) {}
