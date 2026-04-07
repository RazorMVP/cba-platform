package com.cba.share;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "share_products")
@Getter @Setter @NoArgsConstructor
public class ShareProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", nullable = false, unique = true, length = 10)
    private String shortName;

    private String description;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "USD";

    @Column(name = "total_shares")
    private Long totalShares;

    @Column(name = "shares_issued")
    private Long sharesIssued = 0L;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "nominal_shares")
    private Long nominalShares;

    @Column(name = "minimum_shares")
    private Long minimumShares;

    @Column(name = "maximum_shares")
    private Long maximumShares;

    @Column(name = "minimum_active_period_frequency")
    private Integer minimumActivePeriodFrequency;

    @Column(name = "minimum_active_period_frequency_type", length = 20)
    private String minimumActivePeriodFrequencyType;

    @Column(name = "lock_in_period_frequency")
    private Integer lockInPeriodFrequency;

    @Column(name = "lock_in_period_frequency_type", length = 20)
    private String lockInPeriodFrequencyType;

    @Column(name = "allow_dividends_for_inactive")
    private boolean allowDividendsForInactive = false;

    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
