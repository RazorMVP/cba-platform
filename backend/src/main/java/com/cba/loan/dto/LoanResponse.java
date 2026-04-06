package com.cba.loan.dto;

import com.cba.loan.LoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        String loanAccountNumber,
        UUID customerId,
        String customerName,
        String productName,
        BigDecimal principalAmount,
        BigDecimal approvedAmount,
        BigDecimal outstandingBalance,
        BigDecimal interestRate,
        int termMonths,
        LoanStatus status,
        LocalDate applicationDate,
        LocalDate approvalDate,
        LocalDate disbursementDate,
        LocalDate maturityDate
) {}
