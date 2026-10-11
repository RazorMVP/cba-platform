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

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNo ASC")
    private List<LoanRepaymentSchedule> repaymentSchedule = new ArrayList<>();
}
