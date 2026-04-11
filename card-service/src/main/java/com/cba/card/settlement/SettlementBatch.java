package com.cba.card.settlement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dual-message settlement batch.
 *
 * <p>Lifecycle: OPEN → CLOSED → SETTLED | FAILED
 *
 * <p>A batch is opened at start-of-day, accumulates authorization records
 * as settlement items throughout the day, then is closed and settled
 * via end-of-day batch upload (ISO 8583 MTI 0320/0322/0324).
 */
@Entity
@Table(name = "settlement_batches")
@Getter @Setter @NoArgsConstructor
public class SettlementBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique batch reference (UUID as string). */
    @Column(name = "batch_ref", nullable = false, unique = true, length = 36)
    private String batchRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementBatchStatus status = SettlementBatchStatus.OPEN;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "item_count", nullable = false)
    private int itemCount = 0;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Version
    private long version;
}
