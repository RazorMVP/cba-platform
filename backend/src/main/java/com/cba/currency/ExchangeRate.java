package com.cba.currency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Admin-managed exchange rate table.
 * Convention: 1 fromCurrency = rate toCurrency
 * Example: from=USD, to=KES, rate=135.50 means 1 USD = 135.50 KES
 *
 * There is one active row per currency pair. Updating a rate replaces the
 * existing row and resets the updatedAt timestamp (full history in audit_log).
 */
@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    /**
     * Rate: 1 fromCurrency = this many toCurrency units.
     * Stored with 8 decimal places for precision on exotic pairs.
     */
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
