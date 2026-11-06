package com.cba.openbanking;

import com.cba.customer.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "open_banking_consents")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OpenBankingConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "consent_id", unique = true, nullable = false, length = 50)
    private String consentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "tpp_client_id", nullable = false, length = 100)
    private String tppClientId;

    @ElementCollection
    @CollectionTable(name = "consent_scopes", joinColumns = @JoinColumn(name = "consent_id"))
    @Column(name = "scope")
    private List<String> scopes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ConsentStatus status = ConsentStatus.AWAITING_AUTHORISATION;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    // ── Payment initiation (PISP consents only; null otherwise) ──────────────
    // UK OB domestic-payment-consent: the payment must match these, and uses the consent up.

    @Column(name = "instructed_amount", precision = 19, scale = 4)
    private BigDecimal instructedAmount;

    @Column(name = "instructed_currency", length = 3)
    private String instructedCurrency;

    @Column(name = "creditor_account_id")
    private UUID creditorAccountId;

    /** Optional: when set, the payment must come from this account. */
    @Column(name = "debtor_account_id")
    private UUID debtorAccountId;

    @Column(name = "payment_reference", length = 140)
    private String paymentReference;

    /** The payment made under this consent; set when the consent is CONSUMED. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public boolean hasInitiation() {
        return instructedAmount != null;
    }

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
