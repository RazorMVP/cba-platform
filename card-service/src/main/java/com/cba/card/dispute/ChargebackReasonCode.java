package com.cba.card.dispute;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Scheme-specific chargeback reason code catalogue entry.
 *
 * <p>Stores the reason code definitions for all supported card schemes.
 * These are seeded by Flyway migration V6 and treated as read-only reference
 * data at runtime — admin updates require a new migration.
 *
 * <p>Timeframe fields are in calendar days from the reference date (typically
 * the original transaction date). Schemes measure from different reference
 * points — these values represent the most common case.
 */
@Entity
@Table(name = "chargeback_reason_codes")
@Getter @Setter @NoArgsConstructor
public class ChargebackReasonCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Card scheme: VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY. */
    @Column(nullable = false, length = 20)
    private String scheme;

    /** Scheme-defined reason code (e.g. "10.1", "4853", "AFR-01"). */
    @Column(nullable = false, length = 15)
    private String code;

    /** Human-readable description of the reason code. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** High-level category: FRAUD, AUTHORIZATION, PROCESSING_ERROR, CONSUMER_DISPUTES. */
    @Column(nullable = false, length = 50)
    private String category;

    /**
     * Maximum calendar days from the transaction date by which the issuer
     * must initiate a formal chargeback with the scheme.
     */
    @Column(nullable = false)
    private int maxDaysToChargeback;

    /**
     * Maximum calendar days the acquirer has to respond after the chargeback
     * is initiated (e.g. by filing a representment).
     */
    @Column(nullable = false)
    private int maxDaysToRespond;

    /**
     * Maximum calendar days after a representment that the issuer has to
     * escalate to pre-arbitration before the acquirer wins by default.
     */
    @Column(nullable = false)
    private int maxDaysPreArbitration;
}
