package com.cba.charge;

import com.cba.loan.Loan;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_charges")
@Getter @Setter @NoArgsConstructor
public class LoanCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    // Lazy proxies: never serialised (no Hibernate Jackson module; the loan graph holds PII).
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_definition_id")
    private ChargeDefinition chargeDefinition;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_time_type", nullable = false, length = 30)
    private ChargeDefinition.ChargeTimeType chargeTimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "charge_calculation", nullable = false, length = 30)
    private ChargeDefinition.ChargeCalculation chargeCalculation = ChargeDefinition.ChargeCalculation.FLAT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_waived", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountWaived = BigDecimal.ZERO;

    @Column(name = "amount_outstanding", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountOutstanding = BigDecimal.ZERO;

    private boolean penalty = false;
    private boolean paid = false;
    private boolean waived = false;

    @Column(name = "due_for_collection_as_of_date")
    private LocalDate dueForCollectionAsOfDate;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    /**
     * When the charge became income (DR fees receivable / CR income). Penalties: when
     * charged. Fees: when due. Null until then; nothing can be paid against it before.
     */
    @Column(name = "income_recognized_on")
    private LocalDate incomeRecognizedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PrePersist
    public void prePersist() { this.amountOutstanding = this.amount; }

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
