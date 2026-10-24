package com.cba.account;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Funds hold — reserves amount on account without moving the ledger balance.
 * availableBalance = account.balance - sum(ACTIVE holds on that account).
 */
@Entity
@Table(name = "account_holds")
@Getter
@Setter
@NoArgsConstructor
public class AccountHold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    /** Caller's idempotency key (card authorizations); unique per account. Null for teller holds. */
    @Column(name = "external_reference", length = 64)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountHoldStatus status = AccountHoldStatus.ACTIVE;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by", length = 100)
    private String releasedBy;

    /** Card clearing: the amount debited (at most {@link #amount}, the authorized amount). */
    @Column(name = "captured_amount", precision = 19, scale = 4)
    private BigDecimal capturedAmount;

    @Column(name = "captured_at")
    private Instant capturedAt;

    /** Card refund: the amount credited back (at most {@link #capturedAmount}; one refund per hold). */
    @Column(name = "refunded_amount", precision = 19, scale = 4)
    private BigDecimal refundedAmount;

    /** card-service's key for the reversal that refunded it — only that reversal's retry is "already done". */
    @Column(name = "refund_reference", length = 64)
    private String refundReference;

    /** Card scheme of the transaction (VISA, MASTERCARD, VERVE, AFRIGO, UNIONPAY), when card-service sent one. */
    @Column(name = "card_scheme", length = 20)
    private String cardScheme;

    /** Interchange posted with the debit (major units; null = none — debits before V64). */
    @Column(name = "interchange_amount", precision = 19, scale = 4)
    private BigDecimal interchangeAmount;

    /** EARNED (purchase: income) or PAID (ATM cash at another bank's machine: expense). */
    @Enumerated(EnumType.STRING)
    @Column(name = "interchange_direction", length = 6)
    private CardInterchangeDirection interchangeDirection;

    /** Card scheme fees posted with the debit (major units). */
    @Column(name = "scheme_fee_amount", precision = 19, scale = 4)
    private BigDecimal schemeFeeAmount;

    /** Interchange reversed by the refund (in proportion to the amount refunded). */
    @Column(name = "interchange_reversed", precision = 19, scale = 4)
    private BigDecimal interchangeReversed;

    /** Scheme fees reversed by the refund. */
    @Column(name = "scheme_fee_reversed", precision = 19, scale = 4)
    private BigDecimal schemeFeeReversed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;
}
