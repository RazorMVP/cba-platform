package com.cba.card.bureau;

import com.cba.card.card.Card;
import com.cba.card.card.PhysicalCardOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-card entry within a bureau personalization job.
 *
 * <p>{@code personalizationDataHash} is the SHA-256 hex digest of the full CDP
 * record bytes generated for this card. The bureau verifies this hash against the
 * received CDP file to detect any transmission corruption.
 *
 * <p>{@code chipSerialNo} is populated by the bureau on confirmation — it is the
 * unique serial number burned into the EMV chip during personalization.
 */
@Entity
@Table(name = "bureau_job_items")
@Getter @Setter @NoArgsConstructor
public class BureauJobItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private BureauJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_order_id", nullable = false)
    private PhysicalCardOrder physicalOrder;

    /**
     * SHA-256 hex digest of the CDP record bytes for this card.
     * Used by the bureau to verify file integrity on receipt.
     */
    @Column(name = "personalization_data_hash", nullable = false, length = 64)
    private String personalizationDataHash;

    /** Chip serial number assigned by the bureau after personalization. */
    @Column(name = "chip_serial_no", length = 30)
    private String chipSerialNo;

    /** EMV Application ID used to personalise the chip (scheme-specific). */
    @Column(name = "scheme_aid", nullable = false, length = 32)
    private String schemeAid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BureauJobItemStatus status = BureauJobItemStatus.PENDING;

    /** Populated when status = FAILED with the bureau-reported error reason. */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
