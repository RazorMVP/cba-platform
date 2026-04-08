package com.cba.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "provisioning_criteria_definitions")
@Getter @Setter @NoArgsConstructor
public class ProvisioningCriteriaDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id", nullable = false)
    private ProvisioningCriteria criteria;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "min_age", nullable = false)
    private int minAge = 0;

    @Column(name = "max_age", nullable = false)
    private int maxAge = 30;

    @Column(name = "provision_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal provisionPercentage = BigDecimal.ZERO;

    @Column(name = "liability_account_id")
    private UUID liabilityAccountId;

    @Column(name = "expense_account_id")
    private UUID expenseAccountId;
}
