package com.cba.card.wallet;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A prepaid wallet load or unload (V17), retried with the same reference until the backend answers. */
@Entity
@Table(name = "wallet_movements")
@Getter @Setter @NoArgsConstructor
public class WalletMovement {

    public static final String LOAD = "LOAD";
    public static final String UNLOAD = "UNLOAD";
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "movement_type", nullable = false, length = 10)
    private String movementType;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 10)
    private String status = PENDING;

    @Column(name = "failure_reason", length = 60)
    private String failureReason;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private long version;

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
