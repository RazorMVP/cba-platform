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
 * One refund of a cleared settlement item (backlog 15d, decision D1a), as the backend posted it: a partial or
 * full reversal of a cleared debit, or of cleared prepaid spend. Keyed by the reversal reference card-service
 * sent the backend, so a retried reversal maps to the same row. What it takes back from the scheme
 * ({@link #refundGlNet}, negative) counts in the scheme settlement of the day it happened. Minor units.
 */
@Entity
@Table(name = "settlement_refunds")
@Getter @Setter @NoArgsConstructor
public class SettlementRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private SettlementItem item;

    /** The backend's key for this refund ({@code REV-…}); {@code LEGACY-<item id>} for one recorded before V19. */
    @Column(name = "reversal_reference", nullable = false, length = 80, unique = true)
    private String reversalReference;

    /** The refunded amount (minor units of the item's currency). */
    @Column(name = "amount_minor", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountMinor;

    /** Interchange this refund reversed (minor units). */
    @Column(name = "interchange_reversed", precision = 19, scale = 4)
    private BigDecimal interchangeReversed;

    /** Scheme fees this refund reversed (minor units). */
    @Column(name = "scheme_fee_reversed", precision = 19, scale = 4)
    private BigDecimal schemeFeeReversed;

    /** What the refund takes back from the scheme (minor units, negative). */
    @Column(name = "refund_gl_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundGlNet;

    /** The date the refund counts in a scheme settlement (the day it happened). */
    @Column(name = "refund_settlement_date", nullable = false)
    private LocalDate refundSettlementDate;

    /** The scheme report that settled this refund. */
    @Column(name = "refund_report_id")
    private UUID refundReportId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
