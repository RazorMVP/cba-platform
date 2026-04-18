package com.cba.treasury;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "treasury_placements")
@Getter
@Setter
@NoArgsConstructor
public class TreasuryPlacement {

    public enum PlacementType { FIXED_DEPOSIT, TREASURY_BILL, BOND, CALL_MONEY, REPO }
    public enum Status { PENDING, ACTIVE, MATURED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Column(name = "counterparty_name", nullable = false, length = 200)
    private String counterpartyName;

    @Column(name = "counterparty_bic", length = 20)
    private String counterpartyBic;

    @Enumerated(EnumType.STRING)
    @Column(name = "placement_type", nullable = false, length = 30)
    private PlacementType placementType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal principal;

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "USD";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "expected_return", precision = 19, scale = 4)
    private BigDecimal expectedReturn;

    @Column(name = "actual_return", precision = 19, scale = 4)
    private BigDecimal actualReturn;

    @Column(name = "gl_source_account")
    private UUID glSourceAccount;

    @Column(name = "gl_income_account")
    private UUID glIncomeAccount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    private long version;

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
