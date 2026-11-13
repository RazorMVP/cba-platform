package com.cba.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One refund of a captured card debit (backlog 15d, decision D1a), keyed by card-service's reversal
 * reference: the same reference again is a repeat, any other reference is a new refund, until the
 * captured amount is used up. Append-only. The hold carries the running totals.
 */
@Entity
@Table(name = "card_hold_refunds")
@Getter
@Setter
@NoArgsConstructor
public class CardHoldRefund {

    @Id
    private UUID id;

    @Column(name = "hold_id", nullable = false)
    private UUID holdId;

    /** card-service's reversal reference, or {@code LEGACY-<hold id>} for a refund made without one. */
    @Column(name = "refund_reference", nullable = false, length = 64, unique = true)
    private String refundReference;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Interchange this refund reversed (its cumulative share, major units). */
    @Column(name = "interchange_reversed", precision = 19, scale = 4)
    private BigDecimal interchangeReversed;

    /** Scheme fees this refund reversed. */
    @Column(name = "scheme_fee_reversed", precision = 19, scale = 4)
    private BigDecimal schemeFeeReversed;

    @Column(name = "journal_transaction_id", length = 64)
    private String journalTransactionId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
