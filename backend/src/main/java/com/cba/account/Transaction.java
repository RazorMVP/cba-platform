package com.cba.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable ledger record — never update or delete.
 * No @Version, no setters for financial fields.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate = Instant.now();

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate = LocalDate.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    /** Factory method — the only way to create a transaction */
    public static Transaction of(Account account, TransactionType type,
                                 BigDecimal amount, BigDecimal runningBalance,
                                 String description, String referenceNumber, String createdBy) {
        Transaction tx = new Transaction();
        tx.account = account;
        tx.transactionType = type;
        tx.amount = amount;
        tx.runningBalance = runningBalance;
        tx.currencyCode = account.getCurrencyCode();
        tx.description = description;
        tx.referenceNumber = referenceNumber;
        tx.createdBy = createdBy;
        return tx;
    }
}
