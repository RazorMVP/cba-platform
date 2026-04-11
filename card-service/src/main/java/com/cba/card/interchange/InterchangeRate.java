package com.cba.card.interchange;

import com.cba.card.card.CardType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An interchange rate tier row.
 *
 * <p>Matches scheme × card_type × mcc_category × transaction_type × channel.
 * A {@code null} mcc_category is a catch-all that applies when no row with a
 * specific MCC exists for the same combination.
 */
@Entity
@Table(name = "interchange_rates")
@Getter @Setter @NoArgsConstructor
public class InterchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String scheme;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    /**
     * MCC (Merchant Category Code) this rate applies to.
     * {@code null} = catch-all for any MCC not covered by a specific row.
     */
    @Column(name = "mcc_category", length = 50)
    private String mccCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    /** Percentage rate, e.g. 1.7500 = 1.75%. */
    @Column(name = "rate_percent", nullable = false, precision = 6, scale = 4)
    private BigDecimal ratePercent = BigDecimal.ZERO;

    /** Flat fee in minor units of the transaction currency. */
    @Column(name = "fixed_fee", nullable = false, precision = 10, scale = 4)
    private BigDecimal fixedFee = BigDecimal.ZERO;

    /** Base currency for the fixed_fee (ISO 4217 numeric). */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
