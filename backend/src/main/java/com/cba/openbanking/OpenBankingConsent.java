package com.cba.openbanking;

import com.cba.customer.Customer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
