package com.cba.card.card;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
@Getter @Setter @NoArgsConstructor
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The PAN as stored: {@code ENC1:} ciphertext from {@link CardSecrets} (PCI DSS 4.0 §3.5.1; clear text until
     * backlog 2 PR B). Decrypt only through {@link CardSecrets#decrypt}. Never serialised: a full PAN must not
     * leave in a response (§3.4.1 — use {@code panPrefix} / {@code panSuffix}).
     */
    @JsonIgnore
    @Column(name = "pan_encrypted", nullable = false)
    private String panEncrypted;

    /** HMAC-SHA256(PAN, hmac_key) — used for O(1) lookup without decrypting. */
    @Column(name = "pan_hash", nullable = false, unique = true, length = 64)
    private String panHash;

    /** First 8 digits — unencrypted for BIN routing. */
    @Column(name = "pan_prefix", nullable = false, length = 8)
    private String panPrefix;

    /** Last 4 digits — unencrypted for display masking (****1234). */
    @Column(name = "pan_suffix", nullable = false, length = 4)
    private String panSuffix;

    /** Card expiry in YYMM format. */
    @Column(name = "expiry_date", nullable = false, length = 4)
    private String expiryDate;

    // No CVV: card-service doesn't store it (V23, backlog 2 PR B decision D1a) — the HSM derives and checks it.

    @Column(name = "card_sequence_no", nullable = false)
    private short cardSequenceNo = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CardStatus status = CardStatus.ISSUED;

    @Column(name = "virtual_flag", nullable = false)
    private boolean virtualFlag = false;

    /** Customer UUID in the monolith backend. */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Account UUID (debit/prepaid) or Loan UUID (credit) in the monolith. */
    @Column(name = "linked_entity_id")
    private UUID linkedEntityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    // Skip the Hibernate proxy's synthetic props so Jackson serializes the real
    // CardProduct (lazy-loaded within the open-in-view session) instead of failing
    // on ByteBuddyInterceptor when the association is an uninitialized proxy.
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CardProduct product;

    @Column(name = "pin_retry_count", nullable = false)
    private short pinRetryCount = 0;

    @Column(name = "pin_set", nullable = false)
    private boolean pinSet = false;

    /** Cardholder control: taps (DE22 07x / 91x) are allowed. Enforced in {@code CardAuthorizationService}. */
    @Column(name = "contactless_enabled", nullable = false)
    private boolean contactlessEnabled = true;

    /** Cardholder control: card-not-present use (e-commerce, manual entry) is allowed. */
    @Column(name = "cnp_enabled", nullable = false)
    private boolean cnpEnabled = true;

    /** Cardholder control: use where the acquirer's country (DE19) isn't the card's home country is allowed. */
    @Column(name = "international_enabled", nullable = false)
    private boolean internationalEnabled = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    /** Convenience: masked PAN for logging — e.g. 411111******1111 */
    public String maskedPan() {
        return panPrefix.substring(0, 6) + "******" + panSuffix;
    }

    /**
     * Flat product name for API consumers (the Angular Card List binds
     * {@code card.productName}). Derived from the lazy {@code product}
     * association — resolved within the open-in-view session during
     * serialization. Not persisted ({@code @Transient}).
     */
    @Transient
    @JsonProperty("productName")
    public String getProductName() {
        return product != null ? product.getName() : null;
    }
}
