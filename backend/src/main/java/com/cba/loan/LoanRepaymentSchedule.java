package com.cba.loan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_repayment_schedule")
@Getter
@Setter
@NoArgsConstructor
public class LoanRepaymentSchedule {

    /**
     * OVERDUE is never stored: overdue depends on today's date, so it is derived (due date
     * passed and not settled), and a partly paid overdue instalment stays PARTIALLY_PAID.
     * The value is kept because V1 documents it and clients may still send or match it.
     */
    public enum InstallmentStatus {
        PENDING, PAID, PARTIALLY_PAID, OVERDUE, WAIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestDue;

    @Column(name = "fees_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesDue = BigDecimal.ZERO;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDue;

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "fees_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal feesPaid = BigDecimal.ZERO;

    @Column(name = "total_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    /** Interest of this instalment accrued so far (reaches interestDue on the due date). */
    @Column(name = "interest_accrued", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAccrued = BigDecimal.ZERO;

    /** This instalment period's share of the origination fee (effective interest method), and how much is released. */
    @Column(name = "fee_amortisation", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmortisation = BigDecimal.ZERO;

    @Column(name = "fee_amortised", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeAmortised = BigDecimal.ZERO;

    /** Accrued interest the bank gave up (derecognised, IFRS 9 §3.2.3); stays inside interestDue. */
    @Column(name = "interest_waived", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestWaived = BigDecimal.ZERO;

    /** Future interest the bank gave up, taken out of interestDue (contractual = interestDue + this). */
    @Column(name = "interest_cancelled", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestCancelled = BigDecimal.ZERO;

    /** This period's share of the modification discount (IFRS 9 §5.4.3), and how much has unwound into income. */
    @Column(name = "modification_unwind", nullable = false, precision = 19, scale = 4)
    private BigDecimal modificationUnwind = BigDecimal.ZERO;

    @Column(name = "modification_unwound", nullable = false, precision = 19, scale = 4)
    private BigDecimal modificationUnwound = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    // ── What is still owed: every caller goes through these, so waivers are never missed ──

    public BigDecimal unpaidPrincipal() {
        return principalDue.subtract(principalPaid).max(BigDecimal.ZERO);
    }

    public BigDecimal unpaidInterest() {
        return interestDue.subtract(interestPaid).subtract(interestWaived).max(BigDecimal.ZERO);
    }

    public BigDecimal unpaidFees() {
        return feesDue.subtract(feesPaid).max(BigDecimal.ZERO);
    }

    public BigDecimal unpaid() {
        return unpaidPrincipal().add(unpaidInterest()).add(unpaidFees());
    }

    /** Interest accrued and neither received nor waived (negative when received ahead of accrual). */
    public BigDecimal interestReceivable() {
        return interestAccrued.subtract(interestPaid).subtract(interestWaived);
    }

    /** Nothing more is owed: paid, or the rest was waived. */
    public boolean isSettled() {
        return status == InstallmentStatus.PAID || status == InstallmentStatus.WAIVED;
    }

    /** Total due is what the borrower must pay (net of waived interest); total paid is what was received. */
    public void refreshTotals() {
        totalDue = principalDue.add(interestDue).add(feesDue).subtract(interestWaived);
        totalPaid = principalPaid.add(interestPaid).add(feesPaid);
    }

    /**
     * PAID (or WAIVED, when waived interest closed it) once nothing is owed; otherwise
     * PARTIALLY_PAID when anything was received.
     */
    public void refreshStatus(LocalDate date) {
        refreshTotals();
        if (unpaid().signum() == 0) {
            status = interestWaived.signum() > 0 ? InstallmentStatus.WAIVED : InstallmentStatus.PAID;
            paidDate = date;
        } else if (totalPaid.signum() > 0) {
            status = InstallmentStatus.PARTIALLY_PAID;
        }
    }
}
