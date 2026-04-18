package com.cba.treasury;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "liquidity_snapshots")
@Getter @Setter @NoArgsConstructor
public class LiquiditySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "cash_on_hand", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashOnHand = BigDecimal.ZERO;

    @Column(name = "placements_deployed", nullable = false, precision = 19, scale = 4)
    private BigDecimal placementsDeployed = BigDecimal.ZERO;

    @Column(name = "interbank_lending", nullable = false, precision = 19, scale = 4)
    private BigDecimal interbankLending = BigDecimal.ZERO;

    @Column(name = "interbank_borrowing", nullable = false, precision = 19, scale = 4)
    private BigDecimal interbankBorrowing = BigDecimal.ZERO;

    @Column(name = "net_liquidity_position", nullable = false, precision = 19, scale = 4)
    private BigDecimal netLiquidityPosition;

    @Column(name = "reserve_requirement", precision = 19, scale = 4)
    private BigDecimal reserveRequirement;

    @Column(name = "surplus_deficit", precision = 19, scale = 4)
    private BigDecimal surplusDeficit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
