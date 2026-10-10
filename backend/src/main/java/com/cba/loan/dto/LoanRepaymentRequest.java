package com.cba.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A loan repayment. {@code paymentMethod}: ACCOUNT (default — the linked account, or
 * {@code sourceAccountId} if it names another of the borrower's accounts) or CASH
 * (received at the open teller session {@code tellerSessionId}).
 */
public record LoanRepaymentRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        String note,
        UUID sourceAccountId,
        UUID tellerSessionId
) {
    public LoanRepaymentRequest(BigDecimal amount, LocalDate paymentDate, String paymentMethod,
                                String referenceNumber, String note) {
        this(amount, paymentDate, paymentMethod, referenceNumber, note, null, null);
    }
}
