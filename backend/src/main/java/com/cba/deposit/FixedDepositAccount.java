package com.cba.deposit;

import com.cba.customer.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fixed_deposit_accounts")
@Getter
@Setter
@NoArgsConstructor
public class FixedDepositAccount {

    public enum Status { SUBMITTED, APPROVED, ACTIVE, MATURED, PREMATURE_CLOSURE, CLOSED, REJECTED, WITHDRAWN }
    public enum MaturityInstruction { HOLD_AMOUNT_IN_SAVINGS, TRANSFER_TO_SAVINGS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "account_number", unique = true, length = 30)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private FixedDepositProduct product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.SUBMITTED;

    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "maturity_amount", precision = 19, scale = 4)
    private BigDecimal maturityAmount;

    @Column(name = "deposit_period")
    private int depositPeriod;

    @Column(name = "deposit_period_type", length = 10)
    private String depositPeriodType;

    @Column(name = "nominal_annual_interest_rate", precision = 19, scale = 4)
    private BigDecimal nominalAnnualInterestRate;

    @Column(name = "expected_first_deposit_on_date")
    private LocalDate expectedFirstDepositOnDate;

    @Column(name = "submitted_on_date")
    private LocalDate submittedOnDate;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "activated_on_date")
    private LocalDate activatedOnDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "closed_on_date")
    private LocalDate closedOnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "maturity_instruction", length = 30)
    private MaturityInstruction maturityInstruction;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
