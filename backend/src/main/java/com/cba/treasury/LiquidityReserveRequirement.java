package com.cba.treasury;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "liquidity_reserve_requirements")
@Getter @Setter @NoArgsConstructor
public class LiquidityReserveRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "currency_code", nullable = false, length = 3, unique = true)
    private String currencyCode;

    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "minimum_ratio_percent", precision = 5, scale = 2)
    private BigDecimal minimumRatioPercent;

    @Column(name = "alert_threshold_percent", precision = 5, scale = 2)
    private BigDecimal alertThresholdPercent;

    @Column(name = "regulatory_reference", length = 255)
    private String regulatoryReference;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    private Long version;

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
