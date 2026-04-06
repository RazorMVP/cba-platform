package com.cba.product;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_products")
@Getter
@Setter
@NoArgsConstructor
public class LoanProduct extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "min_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal minPrincipal;

    @Column(name = "max_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxPrincipal;

    @Column(name = "min_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal minInterestRate;

    @Column(name = "max_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal maxInterestRate;

    @Column(name = "default_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal defaultInterestRate;

    @Column(name = "min_term_months", nullable = false)
    private int minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private int maxTermMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, length = 30)
    private RepaymentType repaymentType = RepaymentType.ANNUITY;

    @Column(name = "origination_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal originationFee = BigDecimal.ZERO;

    @Column(name = "late_payment_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal latePaymentFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
