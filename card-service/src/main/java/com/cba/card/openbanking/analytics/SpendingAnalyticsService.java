package com.cba.card.openbanking.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Spending analytics aggregated from {@code authorization_log}.
 *
 * <p>All analytics cover approved transactions only (response_code = '00').
 * Queries use JdbcTemplate to avoid coupling to the card domain repositories.
 */
@Service
@RequiredArgsConstructor
public class SpendingAnalyticsService {

    private final JdbcTemplate jdbc;

    // ── MCC → Human-readable category ────────────────────────────────────────

    private static final Map<String, String> MCC_CATEGORIES;

    static {
        Map<String, String> m = new HashMap<>();
        // Dining
        for (String c : List.of("5812","5813","5814")) m.put(c, "Dining");
        // Travel
        for (String c : List.of("4111","4112","4131","4411","4511","4722","7011","7512")) m.put(c, "Travel");
        // Fuel
        for (String c : List.of("5541","5542","5983")) m.put(c, "Fuel");
        // Grocery
        for (String c : List.of("5411","5412","5422","5441","5451","5462","5499")) m.put(c, "Grocery");
        // Healthcare
        for (String c : List.of("5047","5122","5912","8011","8049","8062","8099")) m.put(c, "Healthcare");
        // Entertainment
        for (String c : List.of("7832","7922","7996","7997","7999")) m.put(c, "Entertainment");
        // Retail / Apparel
        for (String c : List.of("5600","5611","5621","5631","5651","5661","5691","5699","5310","5311","5999")) m.put(c, "Retail");
        // Utilities
        for (String c : List.of("4812","4813","4814","4899","4900")) m.put(c, "Utilities");
        // ATM / Cash
        for (String c : List.of("6010","6011","6012")) m.put(c, "ATM/Cash");
        MCC_CATEGORIES = Collections.unmodifiableMap(m);
    }

    public static String categoryFor(String mcc) {
        return MCC_CATEGORIES.getOrDefault(mcc, "Other");
    }

    // ── By-category aggregation ───────────────────────────────────────────────

    /**
     * Total spend and transaction count grouped by merchant category, for a card
     * in the given date range.
     */
    @Transactional(readOnly = true)
    public List<CategorySummary> byCategory(UUID cardId, LocalDate from, LocalDate to,
                                            String currencyCode) {
        String sql = """
                SELECT mcc, currency_code, SUM(amount) AS total, COUNT(*) AS txn_count
                FROM authorization_log
                WHERE card_id = ?
                  AND response_code = '00'
                  AND created_at::date BETWEEN ? AND ?
                  AND (? IS NULL OR currency_code = ?)
                GROUP BY mcc, currency_code
                ORDER BY total DESC
                """;

        Map<String, CategorySummary> byCategory = new LinkedHashMap<>();

        jdbc.query(sql, rs -> {
            String mcc      = rs.getString("mcc");
            String ccy      = rs.getString("currency_code");
            BigDecimal total = rs.getBigDecimal("total");
            int count       = rs.getInt("txn_count");
            String cat      = categoryFor(mcc);

            byCategory.merge(cat,
                    new CategorySummary(cat, total, count, ccy),
                    (a, b) -> new CategorySummary(a.category(),
                            a.totalAmount().add(b.totalAmount()),
                            a.transactionCount() + b.transactionCount(), a.currencyCode()));
        }, cardId, from, to, currencyCode, currencyCode);

        return new ArrayList<>(byCategory.values());
    }

    // ── By-merchant aggregation ───────────────────────────────────────────────

    /** Top merchants by total spend for a card. */
    @Transactional(readOnly = true)
    public List<MerchantSummary> byMerchant(UUID cardId, LocalDate from, LocalDate to,
                                             String currencyCode) {
        String sql = """
                SELECT merchant_id, merchant_name, currency_code,
                       SUM(amount) AS total, COUNT(*) AS txn_count
                FROM authorization_log
                WHERE card_id = ?
                  AND response_code = '00'
                  AND created_at::date BETWEEN ? AND ?
                  AND (? IS NULL OR currency_code = ?)
                GROUP BY merchant_id, merchant_name, currency_code
                ORDER BY total DESC
                LIMIT 20
                """;

        return jdbc.query(sql, (rs, i) -> new MerchantSummary(
                rs.getString("merchant_id"),
                rs.getString("merchant_name"),
                rs.getBigDecimal("total"),
                rs.getInt("txn_count"),
                rs.getString("currency_code")
        ), cardId, from, to, currencyCode, currencyCode);
    }

    // ── Monthly summary ───────────────────────────────────────────────────────

    /** Aggregate monthly stats for a card: spend totals, approve vs decline ratio, avg txn. */
    @Transactional(readOnly = true)
    public MonthlySummary monthlySummary(UUID cardId, LocalDate from, LocalDate to,
                                          String currencyCode) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN response_code = '00' THEN amount END), 0) AS approved_total,
                    COALESCE(COUNT(CASE WHEN response_code = '00' THEN 1 END), 0)    AS approved_count,
                    COALESCE(COUNT(CASE WHEN response_code != '00' THEN 1 END), 0)   AS declined_count,
                    COALESCE(AVG(CASE WHEN response_code = '00' THEN amount END), 0) AS avg_txn
                FROM authorization_log
                WHERE card_id = ?
                  AND created_at::date BETWEEN ? AND ?
                  AND (? IS NULL OR currency_code = ?)
                """;

        return jdbc.queryForObject(sql, (rs, i) -> new MonthlySummary(
                rs.getBigDecimal("approved_total"),
                rs.getInt("approved_count"),
                rs.getInt("declined_count"),
                rs.getBigDecimal("avg_txn"),
                currencyCode
        ), cardId, from, to, currencyCode, currencyCode);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record CategorySummary(String category, BigDecimal totalAmount,
                                   int transactionCount, String currencyCode) {}

    public record MerchantSummary(String merchantId, String merchantName,
                                   BigDecimal totalAmount, int transactionCount,
                                   String currencyCode) {}

    public record MonthlySummary(BigDecimal approvedTotal, int approvedCount,
                                  int declinedCount, BigDecimal avgTransactionAmount,
                                  String currencyCode) {}
}
