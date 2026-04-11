package com.cba.card.interchange;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A scheme-level fee charged by the card network on every transaction.
 *
 * <p>Unlike interchange (issuer↔acquirer), scheme fees are paid to the network
 * by both parties. Multiple active rows for the same scheme and fee_type can
 * exist (e.g. different rates for different date ranges), but the qualification
 * engine only applies the currently active one.
 */
@Entity
@Table(name = "scheme_fees")
@Getter @Setter @NoArgsConstructor
public class SchemeFee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String scheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 30)
    private SchemeFeeType feeType;

    /** Percentage rate applied to gross transaction amount. */
    @Column(name = "rate_percent", nullable = false, precision = 6, scale = 4)
    private BigDecimal ratePercent = BigDecimal.ZERO;

    /** Flat fee per transaction in the currency of the transaction. */
    @Column(name = "fixed_fee", nullable = false, precision = 10, scale = 4)
    private BigDecimal fixedFee = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
