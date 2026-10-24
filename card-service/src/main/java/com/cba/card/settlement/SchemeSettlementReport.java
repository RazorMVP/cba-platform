package com.cba.card.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A card scheme's settlement report for one date and currency, as operations recorded it (backlog 15b).
 * It is recorded only when it equals the expected net of that day's items and refunds; RECORDED once the
 * backend posted the cash leg, POSTING while that call is outstanding (a retry of the same report
 * finishes it).
 */
@Entity
@Table(name = "scheme_settlement_reports")
@Getter @Setter @NoArgsConstructor
public class SchemeSettlementReport {

    public static final String POSTING = "POSTING";
    public static final String RECORDED = "RECORDED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String scheme;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    /** ISO 4217 alphabetic. */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Major units, signed: positive = the bank pays the scheme. */
    @Column(name = "reported_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal reportedNet;

    @Column(name = "expected_net", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedNet;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "refund_count", nullable = false)
    private int refundCount;

    @Column(nullable = false, length = 10)
    private String status;

    /** The idempotency key sent to the backend (one per scheme, date and currency). */
    @Column(name = "backend_reference", nullable = false, unique = true, length = 64)
    private String backendReference;

    @Column(name = "journal_transaction_id", length = 64)
    private String journalTransactionId;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Version
    private Long version;
}
