package com.cba.product;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "deposit_products")
@Getter
@Setter
@NoArgsConstructor
public class DepositProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private DepositAccountType accountType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_compounding", nullable = false, length = 20)
    private InterestCompounding interestCompounding = InterestCompounding.MONTHLY;

    @Column(nullable = false)
    private boolean active = true;
}
