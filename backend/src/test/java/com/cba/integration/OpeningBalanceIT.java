package com.cba.integration;

import com.cba.account.AccountService;
import com.cba.accounting.GlAccount;
import com.cba.accounting.GlAccountRepository;
import com.cba.accounting.GlAccountingService;
import com.cba.accounting.GlAccountingService.JournalLine;
import com.cba.accounting.JournalEntry;
import com.cba.accounting.OpeningBalanceService;
import com.cba.accounting.OpeningBalanceService.OpeningBalances;
import com.cba.accounting.OpeningBalanceService.OpeningLine;
import com.cba.accounting.OpeningBalanceService.Scope;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * The opening-balance journal over its whole life, against a real ledger: preview,
 * post once, reconcile, reject a second post, then load the rest of the legacy trial
 * balance against the clearing account until it is zero.
 *
 * <p>One test method, because the journal can be posted only once per database and
 * the shared test database outlives this class. Other tests' accounts are inserted
 * without GL history too, so assertions are invariants, not totals.
 */
@DisplayName("Opening-balance journal — legacy deposit balances brought into the ledger")
class OpeningBalanceIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired AccountService accountService;
    @Autowired GlAccountingService gl;
    @Autowired GlAccountRepository glAccountRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("preview → post once → reconciled → second post rejected → clearing loaded to zero")
    void openingBalanceLifecycle() {
        // Legacy balances: inserted directly, so no journal ever recorded them.
        UUID usd = account("OB-USD", "USD", "500.00");
        account("OB-KES", "KES", "2000.00");
        account("OB-OD", "USD", "-30.00"); // an overdraft: an asset, not a negative deposit

        OpeningBalances preview = openingBalanceService.preview(Scope.DEPOSITS);
        assertThat(preview.posted()).isFalse();
        assertThat(preview.clearingGlCode()).isEqualTo("3900");
        assertThat(preview.lines()).allSatisfy(l ->
                assertThat(l.openingAmount()).isEqualByComparingTo(l.customerBalance().subtract(l.ledgerBalance())));
        assertThat(line(preview, "2001", "USD").openingAmount()).as("deposits: a credit").isNegative();
        assertThat(line(preview, "2001", "KES").openingAmount()).isNegative();
        assertThat(line(preview, "1102", "USD").customerBalance()).as("overdraft: a debit")
                .isGreaterThanOrEqualTo(new BigDecimal("30.00"));

        OpeningBalances posted = openingBalanceService.post(Scope.DEPOSITS);

        assertThat(posted.posted()).isTrue();
        assertThat(posted.transactionId()).isNotNull();
        assertBalancedPerCurrency(posted.transactionId());
        Map<String, BigDecimal> openingByCurrency = new TreeMap<>();
        posted.lines().forEach(l -> openingByCurrency.merge(l.currency(), l.openingAmount(), BigDecimal::add));
        // A currency already reconciled (net opening 0) has no clearing line at all.
        openingByCurrency.forEach((ccy, net) -> assertThat(posted.clearingBalance().getOrDefault(ccy, BigDecimal.ZERO))
                .as("clearing offsets the %s openings", ccy).isEqualByComparingTo(net.negate()));
        assertThat(posted.clearingIsZero()).as("legacy trial balance not loaded yet").isFalse();

        // Now every control account equals its customer balances.
        assertReconciled();

        // Movements after go-live post as they happen, so the ledger stays reconciled.
        accountService.deposit(usd, new BigDecimal("25.00"), "it", "ob-it");
        assertReconciled();

        assertThatThrownBy(() -> openingBalanceService.post(Scope.DEPOSITS))
                .isInstanceOf(CbaException.class).hasMessageContaining("already been posted");

        // The rest of the legacy trial balance lands against the clearing account (here a
        // stand-in: cash at bank). Once it is all loaded, the clearing account is zero.
        GlAccount clearing = glAccountRepository.findByGlCode("3900").orElseThrow();
        GlAccount cash = glAccountRepository.findByGlCode("1001").orElseThrow();
        List<JournalLine> legacy = new ArrayList<>();
        openingBalanceService.preview(Scope.DEPOSITS).clearingBalance().forEach((ccy, balance) -> {
            legacy.add(signed(clearing, balance.negate(), ccy));
            legacy.add(signed(cash, balance, ccy));
        });
        gl.postJournal(legacy, LocalDate.now(), "Legacy trial balance (test stand-in)",
                JournalEntry.EntityType.MANUAL, null, "LEGACY-TB");

        assertThat(openingBalanceService.preview(Scope.DEPOSITS).clearingIsZero()).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertReconciled() {
        OpeningBalances after = openingBalanceService.preview(Scope.DEPOSITS);
        assertThat(after.posted()).isTrue();
        assertThat(after.lines()).allSatisfy(l -> assertThat(l.openingAmount())
                .as("%s %s: ledger %s vs customers %s", l.glCode(), l.currency(), l.ledgerBalance(), l.customerBalance())
                .isEqualByComparingTo("0"));
    }

    private static OpeningLine line(OpeningBalances b, String glCode, String ccy) {
        return b.lines().stream().filter(l -> l.glCode().equals(glCode) && l.currency().equals(ccy)).findFirst()
                .orElseThrow(() -> new AssertionError("no line " + glCode + " " + ccy + " in " + b.lines()));
    }

    private static JournalLine signed(GlAccount account, BigDecimal debitPositive, String ccy) {
        return debitPositive.signum() > 0
                ? JournalLine.debit(account, debitPositive, ccy)
                : JournalLine.credit(account, debitPositive.negate(), ccy);
    }

    private UUID account(String number, String currency, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, ?, DATE '2025-01-01', 'ob-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(balance), currency);
        return id;
    }

    private void assertBalancedPerCurrency(String transactionId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT entry_type, amount, currency_code FROM journal_entries WHERE transaction_id = ?",
                        transactionId)
                .forEach(r -> net.merge((String) r.get("currency_code"),
                        "DEBIT".equals(r.get("entry_type")) ? (BigDecimal) r.get("amount")
                                : ((BigDecimal) r.get("amount")).negate(), BigDecimal::add));
        assertThat(net).isNotEmpty();
        net.forEach((ccy, n) -> assertThat(n).as("net " + ccy).isEqualByComparingTo("0"));
    }
}
