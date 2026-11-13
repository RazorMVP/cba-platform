package com.cba.cardprepaid.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** A refund (reversal) of a cleared prepaid purchase: any number per clearing, together at most what was cleared. */
public record PrepaidRefundRequest(
        @NotBlank String refundReference,
        BigDecimal amount,
        String currencyCode) {}
