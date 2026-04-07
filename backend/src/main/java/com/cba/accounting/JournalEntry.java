package com.cba.accounting;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter @Setter @NoArgsConstructor
public class JournalEntry {

    public enum EntryType { DEBIT, CREDIT }
    public enum EntityType { LOAN, ACCOUNT, TELLER_CASH, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private GlAccount glAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** ISO date for the business date of the transaction (may differ from posted_at for CoB). */
    @Column(name = "transaction_date", nullable = false)
    private java.time.LocalDate transactionDate;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt = Instant.now();

    /** Which domain entity originated this entry (for traceability). */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20)
    private EntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = 500)
    private String description;

    @Column(name = "is_reversed", nullable = false)
    private boolean reversed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_id")
    private JournalEntry reversalOf;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;
}
