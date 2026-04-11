package com.cba.card.bureau;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A card personalization bureau job — one batch submission to the production bureau.
 *
 * <p>Each job groups a set of {@link BureauJobItem}s (one per physical card) and tracks
 * the batch through its lifecycle from {@code PENDING} assembly through {@code SENT}
 * transmission to bureau {@code CONFIRMED} production completion.
 *
 * <p>The actual CDP (Card Data Preparation) file is transmitted out-of-band as an
 * encrypted payload to the bureau's SFTP endpoint. This entity tracks the job metadata
 * and integrity hash, not the file contents.
 */
@Entity
@Table(name = "bureau_jobs")
@Getter @Setter @NoArgsConstructor
public class BureauJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique batch reference — either generated internally ({@code CBA-YYYYMMDD-NNNNNN})
     * or assigned by the bureau on confirmation.
     */
    @Column(name = "batch_ref", nullable = false, unique = true, length = 40)
    private String batchRef;

    /** Configured bureau name, e.g. {@code THALES_HID}, {@code IDEMIA}, {@code HID_GLOBAL}. */
    @Column(name = "bureau_name", nullable = false, length = 100)
    private String bureauName;

    /** Number of cards in this batch (denormalised for fast reporting). */
    @Column(name = "card_count", nullable = false)
    private int cardCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BureauJobStatus status = BureauJobStatus.PENDING;

    /** Timestamp when the CDP file was transmitted to the bureau. */
    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    /** Timestamp when the bureau confirmed production complete. */
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** Ops notes — populated on FAILED with error detail. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BureauJobItem> items = new ArrayList<>();

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
