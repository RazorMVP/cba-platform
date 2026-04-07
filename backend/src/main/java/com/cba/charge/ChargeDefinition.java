package com.cba.charge;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "charge_definitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChargeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_applies_to", nullable = false, length = 20)
    private ChargeAppliesTo chargeAppliesTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_time_type", nullable = false, length = 30)
    private ChargeTimeType chargeTimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_calculation", nullable = false, length = 30)
    private ChargeCalculation chargeCalculation = ChargeCalculation.FLAT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean penalty = false;

    @Column(name = "free_withdrawal", nullable = false)
    private boolean freeWithdrawal = false;

    @Column(name = "free_withdrawal_charge_frequency")
    private Integer freeWithdrawalChargeFrequency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }

    public enum ChargeAppliesTo { LOAN, SAVINGS, CLIENT, SHARE }
    public enum ChargeTimeType {
        DISBURSEMENT, SPECIFIED_DUE_DATE, INSTALLMENT_FEE, OVERDUE_INSTALLMENT,
        ANNUAL_FEE, MONTHLY_FEE, WITHDRAWAL_FEE, SAVINGS_ACTIVATION, SHARE_PURCHASE
    }
    public enum ChargeCalculation { FLAT, PERCENT_OF_AMOUNT, PERCENT_OF_INTEREST, PERCENT_OF_AMOUNT_AND_INTEREST }
}
