package com.cba.cardprepaid.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** A refund (reversal) of a cleared prepaid purchase: once per clearing, at most what was cleared. */
public record PrepaidRefundRequest(
        @NotBlank String refundReference,
        BigDecimal amount,
        String currencyCode) {}
