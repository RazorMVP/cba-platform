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

    /** Interchange on the transaction (V13: major units of {@link #currencyCode}; direction below). */
    @Column(name = "interchange_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal interchangeAmount = BigDecimal.ZERO;

    /** Total scheme assessment fees for this transaction. */
    @Column(name = "scheme_fee_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal schemeFeeAmount = BigDecimal.ZERO;

    /**
     * V13: what the issuer owes the scheme — gross − interchange earned + interchange paid + fees. Rows
     * before V13 hold the acquirer view (gross − interchange − fees, DE4 minor units).
     */
    @Column(name = "net_settlement_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netSettlementAmount = BigDecimal.ZERO;

    /** Human-readable description of the rate row used (for audit / dispute support). */
    @Column(name = "rate_applied", length = 200)
    private String rateApplied;

    /** Transaction amount, major units (V13). */
    @Column(name = "gross_amount", precision = 19, scale = 4)
    private BigDecimal grossAmount;

    /** ISO 4217 alphabetic (V13). */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "interchange_direction", length = 6)
    private InterchangeDirection interchangeDirection;

    /** The settlement item it was calculated for at clearing (null for the diagnostic calculation). */
    @Column(name = "settlement_item_id")
    private UUID settlementItemId;

    @Column(name = "calculated_at", nullable = false, updatable = false)
    private OffsetDateTime calculatedAt = OffsetDateTime.now();
}
