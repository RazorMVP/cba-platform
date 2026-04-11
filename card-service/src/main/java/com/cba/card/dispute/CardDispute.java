package com.cba.card.dispute;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a cardholder dispute against a transaction.
 *
 * <p>State machine: RAISED → UNDER_REVIEW → RESOLVED_ISSUER | RESOLVED_ACQUIRER | WITHDRAWN
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

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
