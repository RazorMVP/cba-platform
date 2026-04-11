package com.cba.card.dispute;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a cardholder dispute against a transaction.
 *
 * <h3>Full scheme-compliant state machine</h3>
 * <pre>
 *   RAISED → RETRIEVAL_REQUESTED → CHARGEBACK_INITIATED
 *          → REPRESENTMENT → PRE_ARBITRATION → RESOLVED
 *   Any non-terminal state → WITHDRAWN
 * </pre>
 *
 * <p>When a formal chargeback is initiated, {@code schemeReasonCodeId} is
 * populated and the three deadline fields are calculated from the reason
 * code's timeframe parameters. The {@link ChargebackTimeframeEnforcer}
 * checks these deadlines nightly and auto-escalates where necessary.
 */
@Entity
@Table(name = "card_disputes")
@Getter @Setter @NoArgsConstructor
public class CardDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The card this dispute is raised against. */
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /** Original RRN (DE37) from the disputed transaction. */
    @Column(name = "transaction_ref", nullable = false, length = 12)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_reason", nullable = false, length = 30)
    private DisputeReason disputeReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatus status = DisputeStatus.RAISED;

    /** Customer UUID from the monolith backend. */
    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;

    /** Operations staff user UUID who resolved the dispute. */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    // ── Scheme-compliant chargeback fields ────────────────────────────────────

    /**
     * The scheme reason code attached when {@code CHARGEBACK_INITIATED}.
     * Determines timeframe deadlines and reporting categories.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_reason_code_id")
    private ChargebackReasonCode schemeReasonCode;

    /** ISO 4217 alphabetic currency code of the disputed transaction (e.g. "USD", "KES"). */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /**
     * Scheme deadline by which the issuer must initiate a formal chargeback.
     * Calculated as: transaction date + {@link ChargebackReasonCode#getMaxDaysToChargeback()}.
     * {@code null} until reason code is attached.
     */
    @Column(name = "chargeback_deadline")
    private LocalDate chargebackDeadline;

    /**
     * Acquirer deadline to respond (file a representment) after chargeback initiation.
     * Calculated as: chargeback initiation date + {@link ChargebackReasonCode#getMaxDaysToRespond()}.
     */
    @Column(name = "response_deadline")
    private LocalDate responseDeadline;

    /**
     * Issuer deadline to escalate to pre-arbitration after a representment is filed.
     * Calculated as: representment date + {@link ChargebackReasonCode#getMaxDaysPreArbitration()}.
     */
    @Column(name = "pre_arbitration_deadline")
    private LocalDate preArbitrationDeadline;

    /**
     * Direction of final resolution: "ISSUER" (cardholder wins) or "ACQUIRER" (merchant wins).
     * Set only when {@code status == RESOLVED}.
     */
    @Column(name = "resolution_favor", length = 10)
    private String resolutionFavor;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
