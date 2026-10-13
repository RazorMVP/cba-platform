package com.cba.loan;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Probability of default for an IFRS 9 stage and days-past-due band, supplied by the risk
 * team. Stage 1 is a 12-month PD (IFRS 9 §5.5.5), stages 2 and 3 lifetime PDs (§5.5.3).
 */
@Entity
@Table(name = "ecl_pd_rates")
@Getter
@Setter
@NoArgsConstructor
public class EclPdRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int stage;

    @Column(name = "min_dpd", nullable = false)
    private int minDpd;

    @Column(name = "max_dpd", nullable = false)
    private int maxDpd;

    @Column(name = "pd_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal pdPercentage;

    @Column(length = 200)
    private String description;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public EclPdRate(int stage, int minDpd, int maxDpd, BigDecimal pdPercentage, String description) {
        this.stage = stage;
        this.minDpd = minDpd;
        this.maxDpd = maxDpd;
        this.pdPercentage = pdPercentage;
        this.description = description;
    }
}
