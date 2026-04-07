package com.cba.deposit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_deposit_products")
@Getter
@Setter
@NoArgsConstructor
public class RecurringDepositProduct {

    public enum CompoundingPeriod { DAILY, MONTHLY, QUARTERLY, ANNUALLY }
    public enum PostingPeriod { MONTHLY, QUARTERLY, BIANNUAL, ANNUAL }
    public enum CalculationType { DAILY_BALANCE, AVERAGE_DAILY_BALANCE }
    public enum TermType { DAYS, WEEKS, MONTHS, YEARS }
    public enum DepositFrequency { DAILY, WEEKLY, MONTHLY, QUARTERLY, ANNUALLY }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "short_name", length = 20)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "mandatory_recommended_deposit_amount", precision = 19, scale = 4)
    private BigDecimal mandatoryRecommendedDepositAmount;

    @Column(name = "min_deposit_amount", precision = 19, scale = 4)
    private BigDecimal minDepositAmount;

    @Column(name = "max_deposit_amount", precision = 19, scale = 4)
    private BigDecimal maxDepositAmount;

    @Column(name = "nominal_annual_interest_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal nominalAnnualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "compounding_period")
    private CompoundingPeriod compoundingPeriod = CompoundingPeriod.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_period")
    private PostingPeriod postingPeriod = PostingPeriod.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type")
    private CalculationType calculationType = CalculationType.DAILY_BALANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_frequency")
    private DepositFrequency depositFrequency = DepositFrequency.MONTHLY;

    @Column(name = "min_deposit_term")
    private int minDepositTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_deposit_term_type")
    private TermType minDepositTermType = TermType.MONTHS;

    @Column(name = "max_deposit_term")
    private Integer maxDepositTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "max_deposit_term_type")
    private TermType maxDepositTermType = TermType.MONTHS;

    @Column(name = "pre_penalty_applicable")
    private boolean prePenaltyApplicable;

    @Column(name = "pre_penalty_interest", precision = 19, scale = 4)
    private BigDecimal prePenaltyInterest;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
