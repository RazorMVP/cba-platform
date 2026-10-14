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
        LocalDate maturityDate,
        /** Days since the oldest unpaid instalment fell due. */
        int daysPastDue,
        /** 90+ days past due: interest and penalties held in suspense (CBN). */
        boolean nonPerforming,
        BigDecimal interestInSuspense,
        /** Origination fee not yet released to interest income. */
        BigDecimal deferredFeeRemaining,
        /** IFRS 9 stage at the last measurement (1, 2 or 3); null before the first. */
        Integer eclStage,
        /** Expected credit losses held against the loan. */
        BigDecimal lossAllowance,
        /** CBN prudential provision at the last measurement. */
        BigDecimal prudentialProvision,
        LocalDate writtenOffOn,
        String writeOffReason,
        String writeOffApprovalReference,
        /** Gross carrying amount derecognised at write-off. */
        BigDecimal writtenOffAmount,
        /** Cash collected since the write-off. */
        BigDecimal recoveredAmount,
        /** Interest waived: accrued interest derecognised plus future interest given up. */
        BigDecimal interestWaived,
        /** IFRS 9 §5.4.3 modification discount not yet unwound into interest income. */
        BigDecimal modificationDiscountRemaining
) {
    /**
     * The view a borrower may see: the bank's internal credit-risk and accounting figures
     * (suspense, deferred fee, IFRS 9 stage and allowance, CBN provision, write-off reason
     * and board approval, interest waived and the modification discount) are removed. Staff (ADMIN/TELLER) get the full response.
     */
    public LoanResponse forCustomer() {
        return new LoanResponse(id, loanAccountNumber, customerId, customerName, productName,
                principalAmount, approvedAmount, outstandingBalance, interestRate, termMonths, status,
                applicationDate, approvalDate, disbursementDate, maturityDate,
                daysPastDue, false, null, null,
                null, null, null,
                writtenOffOn, null, null, null, recoveredAmount, null, null);
    }
}
