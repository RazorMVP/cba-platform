package com.cba.card.settlement;

import com.cba.card.auth.AuthorizationLog;
import com.cba.card.auth.AuthorizationLogRepository;
import com.cba.card.auth.CardAuthorizationService;
import com.cba.card.auth.CardAuthorizationService.HoldReleaseAnswer;
import com.cba.card.card.Card;
import com.cba.card.card.CardRepository;
import com.cba.card.common.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Settlement reconciliation (backlog 15d, decision D3a): three lists of what the automatic paths left for a
 * person, and the one action that resolves the first.
 * <ul>
 *   <li><b>Unresolved holds</b> — RC 91 declines whose backend hold / 0200 debit call failed and whose
 *       compensating release couldn't reach the backend either ({@code hold_reference} kept on the declined
 *       row). The customer's money may be held, or (0200) taken. {@link #retryHoldRelease} retries that
 *       release and stamps the row once the backend answers.</li>
 *   <li><b>Stuck clearing</b> — items batch close claimed (CLEARING) on an earlier settlement date whose
 *       capture never got an answer. Closing the batch again retries the capture (idempotent).</li>
 *   <li><b>Country unknown</b> — items cleared without DE19, so no cross-border fee was quoted.</li>
 * </ul>
 * Read-only views over card-service's own tables; no PAN, no customer data — the card's last four only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    /** Rows per list, oldest first: the view is a work queue, not a report. */
    static final int MAX_ROWS = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuthorizationLogRepository authorizationLogRepository;
    private final CardRepository cardRepository;
    private final CardAuthorizationService authorizationService;

    /** The three lists. {@code from}/{@code to} (inclusive, optional) bound the authorization date or the batch date. */
    public ReconciliationView view(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw CbaException.badRequest("INVALID_DATE_RANGE", "from must not be after to");
        }
        return new ReconciliationView(from, to, unresolvedHolds(from, to), stuckClearing(from, to),
                countryUnknown(from, to), MAX_ROWS);
    }

    List<UnresolvedHold> unresolvedHolds(LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.card_id, c.pan_suffix, a.mti, a.stan, a.rrn, a.amount, a.currency_code,
                       a.billing_amount, a.billing_currency_code, a.hold_reference, a.created_at
                  FROM authorization_log a
                  LEFT JOIN cards c ON c.id = a.card_id
                 WHERE a.response_code = '91' AND a.hold_reference IS NOT NULL AND a.hold_resolved_at IS NULL
                """);
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (from != null) {
            sql.append(" AND a.created_at >= :fromTs");
            p.addValue("fromTs", startOf(from));
        }
        if (to != null) {
            sql.append(" AND a.created_at < :toTs");
            p.addValue("toTs", startOf(to.plusDays(1)));
        }
        sql.append(" ORDER BY a.created_at, a.id LIMIT ").append(MAX_ROWS);
        return jdbc.query(sql.toString(), p, (rs, i) -> {
            boolean billing = rs.getBigDecimal("billing_amount") != null;
            Money m = Money.of(billing ? rs.getBigDecimal("billing_amount") : rs.getBigDecimal("amount"),
                    billing ? rs.getString("billing_currency_code") : rs.getString("currency_code"));
            return new UnresolvedHold(uuid(rs, "id"), uuid(rs, "card_id"), rs.getString("pan_suffix"),
                    rs.getString("mti"), rs.getString("stan"), rs.getString("rrn"), m.amount(), m.currency(),
                    rs.getString("hold_reference"), time(rs, "created_at"));
        });
    }

    List<StuckClearingItem> stuckClearing(LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.batch_id, b.batch_ref, b.settlement_date, b.status AS batch_status, i.card_id,
                       c.pan_suffix, i.scheme, i.amount, i.currency_code, i.hold_reference, i.authorization_log_id,
                       COALESCE(i.claimed_at, i.created_at) AS claimed_since
                  FROM settlement_items i
                  JOIN settlement_batches b ON b.id = i.batch_id
                  LEFT JOIN cards c ON c.id = i.card_id
                 WHERE i.status = 'CLEARING' AND b.settlement_date < :today
                """);
        MapSqlParameterSource p = new MapSqlParameterSource("today", LocalDate.now());
        dateRange(sql, p, from, to);
        sql.append(" ORDER BY b.settlement_date, i.created_at, i.id LIMIT ").append(MAX_ROWS);
        return jdbc.query(sql.toString(), p, (rs, i) -> {
            Money m = Money.of(rs.getBigDecimal("amount"), rs.getString("currency_code"));
            return new StuckClearingItem(uuid(rs, "id"), uuid(rs, "batch_id"), rs.getString("batch_ref"),
                    rs.getObject("settlement_date", LocalDate.class), rs.getString("batch_status"),
                    uuid(rs, "card_id"), rs.getString("pan_suffix"), rs.getString("scheme"), m.amount(), m.currency(),
                    rs.getString("hold_reference"), uuid(rs, "authorization_log_id"), time(rs, "claimed_since"));
        });
    }

    List<CountryUnknownItem> countryUnknown(LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.batch_id, b.settlement_date, i.scheme, i.status, i.amount, i.currency_code,
                       i.authorization_log_id, i.card_id, c.pan_suffix
                  FROM settlement_items i
                  JOIN settlement_batches b ON b.id = i.batch_id
                  LEFT JOIN cards c ON c.id = i.card_id
                 WHERE i.country_unknown
                """);
        MapSqlParameterSource p = new MapSqlParameterSource();
        dateRange(sql, p, from, to);
        sql.append(" ORDER BY b.settlement_date, i.created_at, i.id LIMIT ").append(MAX_ROWS);
        return jdbc.query(sql.toString(), p, (rs, i) -> {
            Money m = Money.of(rs.getBigDecimal("amount"), rs.getString("currency_code"));
            return new CountryUnknownItem(uuid(rs, "id"), uuid(rs, "batch_id"),
                    rs.getObject("settlement_date", LocalDate.class), rs.getString("scheme"), rs.getString("status"),
                    m.amount(), m.currency(), uuid(rs, "authorization_log_id"), uuid(rs, "card_id"),
                    rs.getString("pan_suffix"));
        });
    }

    /**
     * Retries the release of an unresolved hold with the decline's card-side amount and currency (the
     * backend's release is idempotent and tombstones a reference it never saw). Stamps the row only when
     * the backend answered with a resolution; the HTTP call runs outside any transaction, and the stamp is
     * a conditional update, so of two admins at once one stamps and the other gets HOLD_ALREADY_RESOLVED.
     *
     * @throws CbaException 404 not an unresolved hold; 409 HOLD_ALREADY_RESOLVED, CARD_NOT_LINKED,
     *                      UNKNOWN_CURRENCY or the backend's refusal reason; 503 BACKEND_UNAVAILABLE
     */
    public ResolvedHold retryHoldRelease(UUID authorizationId, String admin) {
        AuthorizationLog row = authorizationLogRepository.findById(authorizationId)
                .orElseThrow(() -> CbaException.notFound("UNRESOLVED_HOLD_NOT_FOUND",
                        "No authorization " + authorizationId));
        if (row.getHoldResolvedAt() != null) {
            throw CbaException.conflict("HOLD_ALREADY_RESOLVED", "Resolved " + row.getHoldResolution()
                    + " by " + row.getHoldResolvedBy() + " at " + row.getHoldResolvedAt());
        }
        if (!"91".equals(row.getResponseCode()) || row.getHoldReference() == null) {
            throw CbaException.notFound("UNRESOLVED_HOLD_NOT_FOUND",
                    "Authorization " + authorizationId + " has no unresolved backend hold");
        }
        Card card = row.getCardId() == null ? null : cardRepository.findById(row.getCardId()).orElse(null);
        if (card == null || card.getLinkedEntityId() == null) {
            throw CbaException.conflict("CARD_NOT_LINKED", "The card has no linked account to release against");
        }
        Currency currency = CardAuthorizationService.currency(row.cardCurrencyCode());
        if (currency == null || row.cardAmount() == null) {
            throw CbaException.conflict("UNKNOWN_CURRENCY", "The authorization's card currency is unknown");
        }
        // Stored at scale 4 (2500.0000): send the decline's own figure (25.00), not 25.000000.
        BigDecimal minor = row.cardAmount().stripTrailingZeros();
        if (minor.scale() < 0) minor = minor.setScale(0);
        BigDecimal major = CardAuthorizationService.toMajorUnits(minor, currency);

        HoldReleaseAnswer answer = authorizationService.retryUnresolvedRelease(card, row.getHoldReference(), major, currency);
        if (!answer.answered()) {
            throw CbaException.serviceUnavailable("BACKEND_UNAVAILABLE",
                    "The core banking system could not be reached; nothing was changed. Try again.");
        }
        String resolution = resolution(answer);
        if (resolution == null) {
            String reason = answer.reason() == null ? "RELEASE_REFUSED" : answer.reason();
            log.warn("Reconciliation: backend refused the release of {} (authorization {}) for {}: {}",
                    row.getHoldReference(), authorizationId, admin, reason);
            throw CbaException.conflict(reason, "The core banking system refused the release: " + reason);
        }
        OffsetDateTime now = OffsetDateTime.now();
        String by = admin == null || admin.isBlank() ? "system" : admin.length() > 100 ? admin.substring(0, 100) : admin;
        if (authorizationLogRepository.markHoldResolved(authorizationId, now, by, resolution) == 0) {
            throw CbaException.conflict("HOLD_ALREADY_RESOLVED", "Another user resolved this hold at the same time");
        }
        log.info("Reconciliation: hold {} of authorization {} (card {}) resolved {} by {}",
                row.getHoldReference(), authorizationId, card.getId(), resolution, by);
        return new ResolvedHold(authorizationId, row.getHoldReference(), resolution, now, by,
                answer.status(), answer.reason());
    }

    /** The backend's answer as a resolution; null when it refused (nothing to stamp). */
    static String resolution(HoldReleaseAnswer a) {
        if (a.approved()) return "REFUNDED".equals(a.status()) ? "REFUNDED" : "RELEASED";
        if ("HOLD_NOT_FOUND".equals(a.reason())) return "NOT_FOUND_TOMBSTONED";
        if ("HOLD_NOT_ACTIVE".equals(a.reason())) return "ALREADY_INACTIVE";
        return null;
    }

    private static void dateRange(StringBuilder sql, MapSqlParameterSource p, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append(" AND b.settlement_date >= :from");
            p.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND b.settlement_date <= :to");
            p.addValue("to", to);
        }
    }

    private static Timestamp startOf(LocalDate d) {
        return Timestamp.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }

    /**
     * Minor units + ISO numeric code → major units + alphabetic code (as the batch tab shows them); an
     * unknown code is passed through with the amount unconverted.
     */
    record Money(BigDecimal amount, String currency) {
        static Money of(BigDecimal minor, String code) {
            Currency c = CardAuthorizationService.currency(code);
            if (minor == null || c == null) return new Money(minor, code);
            return new Money(CardAuthorizationService.toMajorUnits(minor, c), c.getCurrencyCode());
        }
    }

    // ── Views (records, never entities: no PAN, no customer data) ────────────

    /** The three lists, each capped at {@code maxRows}, oldest first. */
    public record ReconciliationView(LocalDate from, LocalDate to, List<UnresolvedHold> unresolvedHolds,
                                     List<StuckClearingItem> stuckClearing, List<CountryUnknownItem> countryUnknown,
                                     int maxRows) {}

    /** An RC 91 decline whose backend reference may still hold (or, 0200, have taken) the customer's money. */
    public record UnresolvedHold(UUID authorizationId, UUID cardId, String panLast4, String mti, String stan,
                                 String rrn, BigDecimal amount, String currency, String holdReference,
                                 OffsetDateTime createdAt) {}

    /** An item claimed for clearing on an earlier date whose backend capture never answered. */
    public record StuckClearingItem(UUID itemId, UUID batchId, String batchRef, LocalDate settlementDate,
                                    String batchStatus, UUID cardId, String panLast4, String scheme,
                                    BigDecimal amount, String currency, String holdReference,
                                    UUID authorizationId, OffsetDateTime claimedSince) {}

    /** An item without the acquirer's country (DE19): no cross-border fee was quoted for it. */
    public record CountryUnknownItem(UUID itemId, UUID batchId, LocalDate settlementDate, String scheme,
                                     String status, BigDecimal amount, String currency, UUID authorizationId,
                                     UUID cardId, String panLast4) {}

    /** The stamped resolution and what the backend answered. */
    public record ResolvedHold(UUID authorizationId, String holdReference, String resolution,
                               OffsetDateTime resolvedAt, String resolvedBy, String backendStatus,
                               String backendReason) {}
}
