package com.cba.card.token;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_vault")
@Getter @Setter @NoArgsConstructor
public class TokenVault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Jasypt AES-256 encrypted DPAN (Device PAN). */
    @Column(name = "dpan_encrypted", nullable = false)
    private String dpanEncrypted;

    /** HMAC-SHA256(DPAN) — used for O(1) lookup during de-tokenization. */
    @Column(name = "dpan_hash", nullable = false, unique = true, length = 64)
    private String dpanHash;

    /** HMAC-SHA256(real PAN) — links back to cards.pan_hash. */
    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;

    /** Externally visible token reference (UUID). Returned to the tokenization requester. */
    @Column(name = "token_ref", nullable = false, unique = true, length = 36)
    private String tokenRef;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
