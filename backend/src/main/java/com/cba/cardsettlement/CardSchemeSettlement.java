package com.cba.cardsettlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A card scheme's net settlement posted to the GL (the cash leg, backlog 15b). card-service's
 * reference is unique, so a repeated request posts nothing (journals carry no idempotency).
 */
@Entity
@Table(name = "card_scheme_settlements")
@Getter
@Setter
@NoArgsConstructor
public class CardSchemeSettlement {

    /** Assigned before the journal is posted (it is the journal's entity id). */
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(nullable = false, length = 20)
    private String scheme;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Signed, major units: positive = the bank paid the scheme (DR 2300 / CR 1003). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "journal_transaction_id", nullable = false, length = 64)
    private String journalTransactionId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
