package com.cba.loan;

import com.cba.account.Account;
import com.cba.common.audit.AuditableEntity;
import com.cba.customer.Customer;
import com.cba.product.LoanProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Getter
@Setter
@NoArgsConstructor
public class Loan extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "loan_account_number", unique = true, nullable = false, length = 25)
    private String loanAccountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id")
    private Account linkedAccount;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "outstanding_balance", precision = 19, scale = 4)
    private BigDecimal outstandingBalance;

    @Column(name = "interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private LoanStatus status = LoanStatus.SUBMITTED;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate = LocalDate.now();

    @Column(name = "approval_date")
    private LocalDate approvalDate;

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "written_off_on")
    private LocalDate writtenOffOn;

    @Column(name = "write_off_reason", columnDefinition = "TEXT")
    private String writeOffReason;

    /** Last day interest (and fee amortisation) was accrued for; null = never. */
    @Column(name = "interest_accrued_through")
    private LocalDate interestAccruedThrough;

    /** Days since the oldest unpaid instalment fell due (0 when nothing is overdue). */
    @Column(name = "days_past_due", nullable = false)
    private int daysPastDue;

    @Column(name = "overdue_since")
    private LocalDate overdueSince;

    /** 90+ days past due: accruals and penalties go to suspense, not income (CBN). */
    @Column(name = "non_performing", nullable = false)
    private boolean nonPerforming;

    @Column(name = "non_performing_since")
    private LocalDate nonPerformingSince;

    /** Interest held in suspense: accrued, unpaid, not income until received. */
    @Column(name = "interest_in_suspense", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestInSuspense = BigDecimal.ZERO;

    /** Origination fees deducted at disbursement (IFRS 9 B5.4.2), and what is still deferred. */
    @Column(name = "deferred_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal deferredFee = BigDecimal.ZERO;

    @Column(name = "deferred_fee_remaining", nullable = false, precision = 19, scale = 4)
    private BigDecimal deferredFeeRemaining = BigDecimal.ZERO;

    /**
     * The original effective interest rate per day (IFRS 9 §5.4.3 discounts modified cash
     * flows at it). Set at disbursement; computed once for loans disbursed before V60.
     */
    @Column(name = "effective_daily_rate")
    private Double effectiveDailyRate;

    /** Modification losses not yet unwound into interest income (GL loan modification discount). */
    @Column(name = "modification_discount_remaining", nullable = false, precision = 19, scale = 4)
    private BigDecimal modificationDiscountRemaining = BigDecimal.ZERO;

    // ── Impairment (IFRS 9) and the CBN prudential provision ─────────────

    /** IFRS 9 stage at the last measurement: 1, 2 or 3; null before the first one. */
    @Column(name = "ecl_stage")
    private Integer eclStage;

    /** Expected credit losses held against the loan (GL loan loss allowance). */
    @Column(name = "loss_allowance", nullable = false, precision = 19, scale = 4)
    private BigDecimal lossAllowance = BigDecimal.ZERO;

    @Column(name = "ecl_measured_on")
    private LocalDate eclMeasuredOn;

    /** CBN prudential provision at the last measurement (feeds the regulatory risk reserve). */
    @Column(name = "prudential_provision", nullable = false, precision = 19, scale = 4)
    private BigDecimal prudentialProvision = BigDecimal.ZERO;

    /** First day the prudential provision covered the whole principal; null when it doesn't. */
    @Column(name = "fully_provided_since")
    private LocalDate fullyProvidedSince;

    // ── Write-off ────────────────────────────────────────────────────────

    /** Board (or delegated authority) approval for the write-off. */
    @Column(name = "write_off_approval_ref", length = 100)
    private String writeOffApprovalReference;

    /** Net carrying amount derecognised at write-off (before the allowance). */
    @Column(name = "written_off_amount", precision = 19, scale = 4)
    private BigDecimal writtenOffAmount;

    /** Cash collected after write-off: impairment gains (IFRS 9 §5.5.8). */
    @Column(name = "recovered_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    /** The write-off journal, reversed line for line by undo. */
    @Column(name = "write_off_transaction_id", length = 64)
    private String writeOffTransactionId;

    /** Loan balances zeroed by the write-off, restored by undo. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "write_off_snapshot", columnDefinition = "jsonb")
    private java.util.Map<String, BigDecimal> writeOffSnapshot;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNo ASC")
    private List<LoanRepaymentSchedule> repaymentSchedule = new ArrayList<>();
}
