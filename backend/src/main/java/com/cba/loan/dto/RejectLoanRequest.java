package com.cba.loan.dto;

import jakarta.validation.constraints.NotBlank;

/** Rejects a loan application before disbursement. */
public record RejectLoanRequest(@NotBlank String reason) {}
