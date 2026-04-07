package com.cba.deposit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deposit_account_transactions")
@Getter
@Setter
@NoArgsConstructor
public class DepositAccountTransaction {

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, INTEREST_POSTING, OVERHEAD_FEE, WITHHOLDING_TAX,
        WAIVE_CHARGES, PRE_CLOSURE, CLOSURE, MATURITY, REINSTATE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "fd_account_id")
    private FixedDepositAccount fdAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "rd_account_id")
    private RecurringDepositAccount rdAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "running_balance", precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "USD";

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    private boolean reversed = false;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
