package com.cba.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record ReversePaymentRequest(@NotBlank String reason) {}
