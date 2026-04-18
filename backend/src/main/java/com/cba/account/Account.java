package com.cba.account;

import com.cba.common.audit.AuditableEntity;
import com.cba.customer.Customer;
import com.cba.product.DepositProduct;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "account_number", unique = true, nullable = false, length = 25)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private DepositProduct product;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.SUBMITTED;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate = LocalDate.now();

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "last_transaction_date")
    private LocalDate lastTransactionDate;

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.lastTransactionDate = LocalDate.now();
    }

    /**
     * Debit validates against effectiveAvailable (total - holds - product floor).
     * Callers must compute effectiveAvailable using {@link #computeEffectiveFloor()}.
     */
    public void debit(BigDecimal amount, BigDecimal effectiveAvailable) {
        if (effectiveAvailable.subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
            throw new com.cba.common.exception.CbaException(
                "INSUFFICIENT_BALANCE",
                "Insufficient available balance in account " + accountNumber +
                " (effective available: " + effectiveAvailable + ")",
                org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }
        this.balance = this.balance.subtract(amount);
        this.lastTransactionDate = LocalDate.now();
    }

    /**
     * Returns the effective balance floor for this account's product.
     * Negative for overdraft-enabled products (balance may go negative up to the limit).
     * Positive for products with a minimum balance requirement.
     */
    public BigDecimal computeEffectiveFloor() {
        if (product == null) return BigDecimal.ZERO;
        if (product.isAllowOverdraft() && product.getOverdraftLimit() != null) {
            return product.getOverdraftLimit().negate();
        }
        return product.getMinimumBalance() != null ? product.getMinimumBalance() : BigDecimal.ZERO;
    }
}
