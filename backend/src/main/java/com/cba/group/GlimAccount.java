package com.cba.group;

import com.cba.customer.Customer;
import com.cba.loan.Loan;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Group Loan Individual Monitoring (GLIM).
 * Links each group member to their individual share of a group loan disbursement.
 */
@Entity
@Table(name = "glim_accounts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"loan_id", "customer_id"}))
@Getter @Setter @NoArgsConstructor
public class GlimAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "individual_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal individualAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";
}
