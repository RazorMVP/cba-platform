package com.cba.card.limits;

import com.cba.card.card.Card;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "card_limits")
@Getter @Setter @NoArgsConstructor
public class CardLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false, unique = true)
    private Card card;

    @Column(name = "daily_purchase_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyPurchaseLimit;

    @Column(name = "daily_withdrawal_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyWithdrawalLimit;

    @Column(name = "per_txn_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal perTxnLimit;

    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyLimit;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }
}
