package com.cba.card.dispute;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A formal documentation request sent to the acquirer before a chargeback
 * is initiated.
 *
 * <p>When a cardholder disputes a transaction, the issuer typically requests
 * the acquirer to provide transaction evidence (sales slip, EMV data, etc.).
 * If the acquirer fails to respond by {@code deadline}, the dispute escalates
 * to a formal chargeback automatically.
 */
@Entity
@Table(name = "retrieval_requests")
@Getter @Setter @NoArgsConstructor
public class RetrievalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The parent dispute this retrieval request belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private CardDispute dispute;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    /**
     * Scheme-mandated deadline for the acquirer to supply the requested
     * documentation. Derived from the reason code's {@code maxDaysToRespond}.
     */
    @Column(nullable = false)
    private LocalDate deadline;

    /** Timestamp when the acquirer supplied the requested documentation. */
    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    /** PENDING → FULFILLED | EXPIRED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
