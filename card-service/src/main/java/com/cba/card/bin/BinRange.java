package com.cba.card.bin;

import com.cba.card.card.CardType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bin_ranges")
@Getter @Setter @NoArgsConstructor
public class BinRange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** First 6 or 8 digits of BIN range (inclusive). */
    @Column(name = "bin_start", nullable = false, length = 8)
    private String binStart;

    /** Last 6 or 8 digits of BIN range (inclusive). */
    @Column(name = "bin_end", nullable = false, length = 8)
    private String binEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SchemeType scheme;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", length = 20)
    private CardType cardType;

    /** ISO 3166 alpha-3 country code. */
    @Column(name = "country_code", length = 3)
    private String countryCode;

    /** ISO 4217 numeric currency code. */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
