package com.cba.card.card;

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

    /** Jasypt AES-256 encrypted full PAN. */
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

    /** Jasypt AES-256 encrypted CVV. */
    @Column(name = "cvv_encrypted", nullable = false)
    private String cvvEncrypted;

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
    private CardProduct product;

    @Column(name = "pin_retry_count", nullable = false)
    private short pinRetryCount = 0;

    @Column(name = "pin_set", nullable = false)
    private boolean pinSet = false;

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
}
