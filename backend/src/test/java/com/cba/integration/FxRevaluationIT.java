package com.cba.integration;

import com.cba.accounting.FxRevaluationService;
import com.cba.accounting.FxRevaluationService.CurrencyRevaluation;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * FX revaluation (IAS 21 §23, §28) against a real ledger. Other integration tests share
 * this database and post their own FX movements, so each test first revalues everything
 * at the current rates and then asserts only on what changes after that.
 */
@DisplayName("FX revaluation — open positions retranslated at the closing rate")
class FxRevaluationIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired FxRevaluationService fxRevaluationService;
    @Autowired PaymentService paymentService;
    @Autowired JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void functionalCurrencyIsUsd() {
        assertThat(jdbc.queryForObject(
                "SELECT string_value FROM global_configurations WHERE name = 'functional-currency'", String.class))
                .as("these tests assume the demo functional currency").isEqualTo("USD");
    }

    @Test
    @DisplayName("a rate rise on a short KES position is a loss; 1201 KES ends at -position × closing rate; a rerun posts nothing")
    void revalue_shortPosition_rateRise() {
        fxRevaluationService.revalue(today); // baseline: everything already at the current rate
        UUID usd = account("FXREV-U", "USD", "1000.00");
        UUID kes = account("FXREV-K", "KES", "1000.00");
        paymentService.transfer(new TransferRequest(usd, kes, new BigDecimal("100.00"), "it", null), "fx-it");

        // Same rate as the transfer: nothing to adjust beyond rounding of the sum.
        CurrencyRevaluation sameRate = kesOf(fxRevaluationService.revalue(today));
        assertThat(sameRate.adjustment().abs()).isLessThanOrEqualTo(new BigDecimal("0.0001"));
        assertThat(sameRate.position()).as("the bank owes KES: a debit (short) position").isPositive();

        BigDecimal original = rate("KES", "USD");
        BigDecimal risen = original.multiply(new BigDecimal("1.10")).setScale(8, RoundingMode.HALF_UP);
        BigDecimal lossBefore = gainLossDebitNet();
        try {
            setRate("KES", "USD", risen);

            CurrencyRevaluation r = kesOf(fxRevaluationService.revalue(today));

            BigDecimal expected = r.position().negate().multiply(risen).setScale(4, RoundingMode.HALF_UP);
            assertThat(r.closingRate()).isEqualByComparingTo(risen);
            assertThat(r.revaluedAmount()).isEqualByComparingTo(expected);
            assertThat(r.adjustment()).as("KES rose against a short position: a loss").isNegative();
            assertThat(r.transactionId()).isNotNull();
            assertThat(equivalentKes()).isEqualByComparingTo(expected);
            assertThat(gainLossDebitNet().subtract(lossBefore))
                    .as("DR 4003 by the adjustment").isEqualByComparingTo(r.adjustment().negate());
            assertThat(lines(r.transactionId())).containsExactlyInAnyOrder(
                    "CREDIT 1201 " + r.adjustment().negate().setScale(4) + " USD KES",
                    "DEBIT 4003 " + r.adjustment().negate().setScale(4) + " USD -");

            // Idempotent on the same day and rate.
            List<CurrencyRevaluation> rerun = fxRevaluationService.revalue(today);
            assertThat(rerun).allSatisfy(x -> assertThat(x.transactionId()).isNull());
        } finally {
            setRate("KES", "USD", original);
            fxRevaluationService.revalue(today); // leave the ledger at the restored rate
        }
    }

    @Test
    @DisplayName("an open position without a closing rate fails the run and posts nothing")
    void revalue_missingRate_postsNothing() {
        UUID usd = account("FXREV-U2", "USD", "1000.00");
        UUID kes = account("FXREV-K2", "KES", "1000.00");
        paymentService.transfer(new TransferRequest(usd, kes, new BigDecimal("50.00"), "it", null), "fx-it");
        int revaluationsBefore = revaluationLineCount();

        jdbc.update("UPDATE exchange_rates SET active = FALSE WHERE from_currency = 'KES' AND to_currency = 'USD'");
        try {
            assertThatThrownBy(() -> fxRevaluationService.revalue(today)).hasMessageContaining("KES");
            assertThat(revaluationLineCount()).isEqualTo(revaluationsBefore);
        } finally {
            jdbc.update("UPDATE exchange_rates SET active = TRUE WHERE from_currency = 'KES' AND to_currency = 'USD'");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static CurrencyRevaluation kesOf(List<CurrencyRevaluation> results) {
        return results.stream().filter(r -> r.currency().equals("KES")).findFirst()
                .orElseThrow(() -> new AssertionError("no KES position revalued: " + results));
    }

    private UUID account(String number, String currency, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, ?, DATE '2025-01-01', 'fx-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(balance), currency);
        return id;
    }

    private BigDecimal rate(String from, String to) {
        return jdbc.queryForObject("SELECT rate FROM exchange_rates WHERE from_currency = ? AND to_currency = ?",
                BigDecimal.class, from, to);
    }

    /** Direct update: the service would also rewrite the inverse, which the test must leave alone. */
    private void setRate(String from, String to, BigDecimal rate) {
        jdbc.update("UPDATE exchange_rates SET rate = ? WHERE from_currency = ? AND to_currency = ?", rate, from, to);
    }

    /** 1201 balance (debits − credits) attributed to KES. */
    private BigDecimal equivalentKes() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = '1201' AND je.position_currency = 'KES'""", BigDecimal.class);
    }

    /** 4003 debits − credits in USD: rises by every revaluation loss. */
    private BigDecimal gainLossDebitNet() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = '4003' AND je.currency_code = 'USD'""", BigDecimal.class);
    }

    private int revaluationLineCount() {
        return jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE entity_type = 'FX_REVALUATION'",
                Integer.class);
    }

    /** "SIDE GLCODE AMOUNT CCY POSITIONCCY" for every line of one journal. */
    private List<String> lines(String transactionId) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                       || ' ' || COALESCE(je.position_currency, '-')
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.transaction_id = ?""", String.class, transactionId);
    }
}
