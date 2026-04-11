package com.cba.card.settlement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Individual transaction record within a settlement batch.
 *
 * <p>Each approved dual-message authorization produces one SettlementItem.
 * Single-message (real-time advice) transactions bypass the batch and are
 * settled immediately.
 */
@Entity
@Table(name = "settlement_items")
@Getter @Setter @NoArgsConstructor
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementBatch batch;

    /** FK to authorization_log — null for manually-added items. */
    @Column(name = "authorization_log_id")
    private UUID authorizationLogId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** PENDING → SETTLED | FAILED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
