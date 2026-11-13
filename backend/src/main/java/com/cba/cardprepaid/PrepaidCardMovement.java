package com.cba.cardprepaid;

import com.cba.account.CardInterchangeDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One prepaid card movement card-service asked the backend to post (V68), idempotent on its reference. */
@Entity
@Table(name = "prepaid_card_movements")
@Getter
@Setter
public class PrepaidCardMovement {

    public enum Type { LOAD, UNLOAD, CLEARING, REFUND }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 10)
    private Type type;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(length = 20)
    private String scheme;

    @Column(name = "interchange_amount", precision = 19, scale = 4)
    private BigDecimal interchangeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "interchange_direction", length = 10)
    private CardInterchangeDirection interchangeDirection;

    @Column(name = "scheme_fee_amount", precision = 19, scale = 4)
    private BigDecimal schemeFeeAmount;

    /** CLEARING: what its refunds gave back so far (running total, at most {@link #amount}). */
    @Column(name = "refunded_amount", precision = 19, scale = 4)
    private BigDecimal refundedAmount;

    /** CLEARING: the reference of its latest refund (every refund is a REFUND row naming it in originalReference). */
    @Column(name = "refund_reference", length = 80)
    private String refundReference;

    /** REFUND: the clearing it reverses. */
    @Column(name = "original_reference", length = 80)
    private String originalReference;

    @Column(name = "journal_transaction_id", nullable = false, length = 64)
    private String journalTransactionId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
