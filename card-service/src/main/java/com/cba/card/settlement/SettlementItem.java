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
 * <p>Every approval that reserved funds produces one item, in the approval's transaction. A
 * debit-card purchase (0100) is PENDING until batch close clears it against its backend hold;
 * a single-message debit (0200) and a prepaid approval are already completed, so they start
 * SETTLED. {@code amount} is DE4 minor units, {@code currencyCode} the DE49 numeric code.
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

    /** PENDING → CLEARING (capture in flight) → SETTLED | FAILED; PENDING/SETTLED → REVERSED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "card_id")
    private UUID cardId;

    /** Backend account debited at clearing (debit cards); null for prepaid. */
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "card_type", length = 20)
    private String cardType;

    /** The backend hold (or single-message debit) this item clears or reverses. */
    @Column(name = "hold_reference", length = 64)
    private String holdReference;

    /** Why clearing was refused (backend reason, e.g. INSUFFICIENT_FUNDS on a late presentment). */
    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;

    /** Card scheme (VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY), from the authorization (backlog 15b). */
    @Column(length = 20)
    private String scheme;

    /** Interchange the backend posted with the debit (minor units, like {@link #amount}). */
    @Column(name = "interchange_amount", precision = 19, scale = 4)
    private BigDecimal interchangeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "interchange_direction", length = 6)
    private com.cba.card.interchange.InterchangeDirection interchangeDirection;

    /** Scheme fees the backend posted with the debit (minor units). */
    @Column(name = "scheme_fee_amount", precision = 19, scale = 4)
    private BigDecimal schemeFeeAmount;

    /**
     * What the bank owes the scheme for this item once cleared (minor units): amount − interchange earned
     * + interchange paid + fees. Null when it moved no GL (prepaid, not cleared).
     */
    @Column(name = "gl_net", precision = 19, scale = 4)
    private BigDecimal glNet;

    /** A refund of the cleared debit (minor units) and the interchange / fees it reversed. */
    @Column(name = "refunded_amount", precision = 19, scale = 4)
    private BigDecimal refundedAmount;

    @Column(name = "interchange_reversed", precision = 19, scale = 4)
    private BigDecimal interchangeReversed;

    @Column(name = "scheme_fee_reversed", precision = 19, scale = 4)
    private BigDecimal schemeFeeReversed;

    /** What the refund took back from the scheme (minor units, negative). */
    @Column(name = "refund_gl_net", precision = 19, scale = 4)
    private BigDecimal refundGlNet;

    /** The date the refund counts in a scheme settlement. */
    @Column(name = "refund_settlement_date")
    private java.time.LocalDate refundSettlementDate;

    /** The scheme report that settled this item's clearing. */
    @Column(name = "settlement_report_id")
    private UUID settlementReportId;

    /** The scheme report that settled its refund. */
    @Column(name = "refund_report_id")
    private UUID refundReportId;

    /** Batch close and reversals can race on an item; the later writer re-reads instead of overwriting. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
