package com.cba.accounting;

import com.cba.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "gl_accounts")
@Getter @Setter @NoArgsConstructor
public class GlAccount extends AuditableEntity {

    public enum AccountType { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }
    public enum Usage { HEADER, DETAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "gl_code", nullable = false, unique = true, length = 20)
    private String glCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Usage usage = Usage.DETAIL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private GlAccount parent;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean disabled = false;

    @Column(nullable = false)
    private boolean manualEntriesAllowed = true;
}
