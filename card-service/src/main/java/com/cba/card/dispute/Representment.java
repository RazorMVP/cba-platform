package com.cba.card.dispute;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An acquirer's counter-argument to a chargeback.
 *
 * <p>When a chargeback is initiated, the acquirer may file a representment
 * providing evidence that the transaction was valid. The issuer then has
 * {@code deadline} days to either accept the representment (closing in the
 * acquirer's favour) or escalate to pre-arbitration.
 *
 * <p>If the issuer misses the deadline, the representment is accepted by
 * default — the acquirer wins without further action.
 */
@Entity
@Table(name = "representments")
@Getter @Setter @NoArgsConstructor
public class Representment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The parent dispute this representment counters. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false)
    private CardDispute dispute;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    /**
     * Deadline by which the issuer must escalate to pre-arbitration or accept
     * the representment. Derived from the reason code's {@code maxDaysPreArbitration}.
     */
    @Column(nullable = false)
    private LocalDate deadline;

    /** Acquirer's stated reason / evidence summary. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** PENDING → ACCEPTED | REJECTED | ESCALATED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
