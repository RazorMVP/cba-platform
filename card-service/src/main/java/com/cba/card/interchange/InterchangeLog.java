package com.cba.card.interchange;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable record of the interchange calculation applied to one authorization.
 *
 * <p>Created by {@link InterchangeQualificationEngine} when an auth is processed.
 * Never updated — if recalculation is needed a new row is written.
 */
@Entity
@Table(name = "interchange_log")
@Getter @Setter @NoArgsConstructor
public class InterchangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "authorization_log_id")
    private UUID authorizationLogId;

    @Column(nullable = false, length = 20)
    private String scheme;

    /** Interchange fee amount (issuer receives / acquirer pays). */
    @Column(name = "interchange_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal interchangeAmount = BigDecimal.ZERO;

    /** Total scheme assessment fees for this transaction. */
    @Column(name = "scheme_fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal schemeFeeAmount = BigDecimal.ZERO;

    /** Gross amount minus interchange minus scheme fees. */
    @Column(name = "net_settlement_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netSettlementAmount = BigDecimal.ZERO;

    /** Human-readable description of the rate row used (for audit / dispute support). */
    @Column(name = "rate_applied", length = 200)
    private String rateApplied;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private OffsetDateTime calculatedAt = OffsetDateTime.now();
}
