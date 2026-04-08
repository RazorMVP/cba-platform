package com.cba.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounting_rules")
@Getter @Setter @NoArgsConstructor
public class AccountingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "debit_account_id")
    private UUID debitAccountId;

    @Column(name = "credit_account_id")
    private UUID creditAccountId;

    @Column(name = "allow_multiple_debits", nullable = false)
    private boolean allowMultipleDebits = false;

    @Column(name = "allow_multiple_credits", nullable = false)
    private boolean allowMultipleCredits = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    private Long version;

    @PreUpdate
    public void preUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
