package com.cba.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What it costs to settle a loan on {@code date}: all outstanding principal, interest and
 * scheduled fees on instalments already due, and charges already recognised. Interest on
 * instalments not yet due has not been earned and is not charged.
 */
public record ForeclosureQuote(
        UUID loanId,
        String loanAccountNumber,
        LocalDate date,
        String currencyCode,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal fees,
        BigDecimal charges,
        BigDecimal total
) {}
