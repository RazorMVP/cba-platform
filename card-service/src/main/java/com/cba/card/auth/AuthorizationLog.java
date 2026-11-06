package com.cba.card.auth;

import com.cba.card.fraud.FraudDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "authorization_log")
@Getter @Setter @NoArgsConstructor
public class AuthorizationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(nullable = false, length = 6)
    private String stan;

    @Column(length = 12)
    private String rrn;

    @Column(nullable = false, length = 4)
    private String mti;

    @Column(name = "processing_code", length = 6)
    private String processingCode;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /** DE6 cardholder billing amount (minor units of {@link #billingCurrencyCode}); null when not sent. */
    @Column(name = "billing_amount", precision = 19, scale = 4)
    private BigDecimal billingAmount;

    /** DE51 cardholder billing currency (ISO 4217 numeric); null when not sent. */
    @Column(name = "billing_currency_code", length = 3)
    private String billingCurrencyCode;

    /** DE10 conversion rate as received — audit only, never recomputed. */
    @Column(name = "conversion_rate", length = 8)
    private String conversionRate;

    /**
     * The amount that touched the cardholder's money: DE6 when the scheme sent it (a foreign-currency
     * transaction converted to the card's currency), else DE4. Minor units of {@link #cardCurrencyCode()}.
     */
    public BigDecimal cardAmount() {
        return billingAmount != null ? billingAmount : amount;
    }

    /** DE51 when the scheme sent a billing amount, else DE49. */
    public String cardCurrencyCode() {
        return billingAmount != null ? billingCurrencyCode : currencyCode;
    }

    @Column(name = "response_code", nullable = false, length = 2)
    private String responseCode;

    @Column(name = "auth_code", length = 6)
    private String authCode;

    @Column(name = "entry_mode", length = 20)
    private String entryMode;

    @Column(name = "terminal_id", length = 8)
    private String terminalId;

    @Column(name = "merchant_id", length = 15)
    private String merchantId;

    @Column(name = "merchant_name", length = 40)
    private String merchantName;

    @Column(length = 4)
    private String mcc;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FraudDecision decision;

    @Column(name = "is_financial", nullable = false)
    private boolean isFinancial = false;

    @Column(length = 20)
    private String scheme;

    /** Backend account hold reserving this approval's amount (debit cards); released on reversal. */
    @Column(name = "hold_reference", length = 64)
    private String holdReference;

    /** Amount taken from the prepaid wallet by this approval (major units); restored on reversal. */
    @Column(name = "wallet_debit", precision = 19, scale = 4)
    private BigDecimal walletDebit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
