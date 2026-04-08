package com.cba.system;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "credit_bureau_product_mappings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"loan_product_id","credit_bureau_id"}))
@Getter @Setter @NoArgsConstructor
public class CreditBureauProductMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_product_id", nullable = false)
    private UUID loanProductId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_bureau_id", nullable = false)
    private CreditBureauIntegration creditBureau;

    @Column(name = "is_creditcheck_mandatory", nullable = false)
    private boolean creditCheckMandatory = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
