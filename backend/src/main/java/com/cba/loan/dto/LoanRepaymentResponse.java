package com.cba.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LoanRepaymentResponse(
        UUID loanId,
        String loanAccountNumber,
        BigDecimal amountPaid,
        BigDecimal principalPortion,
        BigDecimal interestPortion,
        BigDecimal feePortion,
        BigDecimal outstandingBalanceAfter,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber
) {}
