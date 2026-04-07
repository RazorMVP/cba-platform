package com.cba.system;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_components")
@Getter @Setter @NoArgsConstructor
public class TaxComponent {

    public enum CreditAccountType { GL_ACCOUNT }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal percentage;

    @Column(name = "credit_account_type", length = 30)
    private String creditAccountType;

    @Column(name = "credit_account_id")
    private UUID creditAccountId;

    @Column(name = "debit_account_type", length = 30)
    private String debitAccountType;

    @Column(name = "debit_account_id")
    private UUID debitAccountId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
