package com.cba.payment;

import com.cba.account.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "reference_number", unique = true, nullable = false, length = 50)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private Account destinationAccount;

    /** Amount in the source account's currency */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    // --- Cross-currency fields (null for same-currency transfers) ---

    @Column(name = "source_currency", length = 3)
    private String sourceCurrency;

    @Column(name = "source_amount", precision = 19, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "destination_currency", length = 3)
    private String destinationCurrency;

    @Column(name = "destination_amount", precision = 19, scale = 4)
    private BigDecimal destinationAmount;

    @Column(name = "exchange_rate_used", precision = 19, scale = 8)
    private BigDecimal exchangeRateUsed;

    @Column(name = "is_cross_currency", nullable = false)
    private boolean crossCurrency = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "executed_date")
    private Instant executedDate;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // External / SWIFT / SEPA fields (V46 migration — null for internal transfers)
    @Column(name = "external_network", length = 10)
    private String externalNetwork;   // SWIFT | SEPA | ACH

    @Column(name = "beneficiary_name", length = 200)
    private String beneficiaryName;

    @Column(name = "beneficiary_iban", length = 34)
    private String beneficiaryIban;

    @Column(name = "beneficiary_bic", length = 11)
    private String beneficiaryBic;

    @Column(name = "beneficiary_bank_name", length = 200)
    private String beneficiaryBankName;

    @Column(name = "beneficiary_country_code", length = 3)
    private String beneficiaryCountryCode;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    /** SWIFT charge-bearer: SHA (shared), OUR (sender pays all), BEN (beneficiary pays all) */
    @Column(name = "charge_type", length = 5)
    private String chargeType;

    // Reversal tracking (V10 migration)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of")
    private Payment reversalOf;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Backfills the cross-currency audit columns before insert/update.
     *
     * <p>{@code source_currency} and {@code destination_currency} are NOT NULL at the
     * schema level (see {@code V3__multi_currency.sql}) — every payment records the
     * currency on each leg, which for a same-currency transfer is simply the same
     * currency twice. The cross-currency code path sets these explicitly to the
     * differing values; this hook is the safety net that backfills them from the
     * always-set {@link #currencyCode} / {@link #amount} for same-currency transfers,
     * reversals, and external payments — so no {@code new Payment()} call site can
     * ever violate the constraint, present or future.
     */
    @PrePersist
    @PreUpdate
    private void backfillCurrencyAuditColumns() {
        if (sourceCurrency == null)      sourceCurrency = currencyCode;
        if (destinationCurrency == null) destinationCurrency = currencyCode;
        if (sourceAmount == null)        sourceAmount = amount;
        if (destinationAmount == null)   destinationAmount = amount;
    }
}
