package com.cba.loan.dto;

import com.cba.loan.LoanRepaymentSchedule.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RepaymentScheduleResponse(
        UUID id,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal feesDue,
        BigDecimal totalDue,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal totalPaid,
        InstallmentStatus status,
        LocalDate paidDate
) {}
