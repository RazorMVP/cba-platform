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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
