package com.cba.integration;

import com.cba.account.AccountService;
import com.cba.account.dto.TransactionResponse;
import com.cba.common.exception.CbaException;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.ReversePaymentRequest;
import com.cba.payment.dto.TransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Every balance change on a deposit account posts a balanced journal in the same
 * transaction, against the GL accounts V8/V53/V54 seed — so the savings-control GL
 * account moves by exactly what the customer balances move (GL ↔ sub-ledger
 * reconciliation), and a missing mapping rejects the transaction.
 */
@DisplayName("GL posting — deposits, withdrawals and transfers against a real database")
class GlPostingIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired AccountService accountService;
    @Autowired PaymentService paymentService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("deposit: DR 1001 Cash on Hand / CR 2001 Customer Deposits, same reference")
    void deposit_postsCashAgainstDeposits() {
        UUID acc = account("GL-DEP", "USD", "100.00");

        TransactionResponse tx = accountService.deposit(acc, new BigDecimal("40.00"), "it", "gl-it");

        assertThat(lines(acc, tx.referenceNumber())).containsExactlyInAnyOrder(
                "DEBIT 1001 40.0000 USD", "CREDIT 2001 40.0000 USD");
    }

    @Test
    @DisplayName("withdrawal: DR 2001 / CR 1001")
    void withdrawal_postsDepositsAgainstCash() {
        UUID acc = account("GL-WDR", "USD", "500.00"); // demo product floor is 100

        TransactionResponse tx = accountService.withdraw(acc, new BigDecimal("25.00"), "it", "gl-it");

        assertThat(lines(acc, tx.referenceNumber())).containsExactlyInAnyOrder(
                "DEBIT 2001 25.0000 USD", "CREDIT 1001 25.0000 USD");
    }

    @Test
    @DisplayName("reconciliation: GL 2001 moves by exactly the change in the customer balances")
    void reconciliation_controlAccountTracksBalances() {
        UUID a = account("GL-REC-A", "USD", "500.00");
        UUID b = account("GL-REC-B", "USD", "500.00");
        BigDecimal balancesBefore = balance(a).add(balance(b));
        BigDecimal glBefore = depositsGlCredit("USD");

        accountService.deposit(a, new BigDecimal("70.00"), "it", "gl-it");
        accountService.withdraw(b, new BigDecimal("30.00"), "it", "gl-it");
        paymentService.transfer(new TransferRequest(a, b, new BigDecimal("55.00"), "it", null), "gl-it");

        BigDecimal balanceMovement = balance(a).add(balance(b)).subtract(balancesBefore);
        BigDecimal glMovement = depositsGlCredit("USD").subtract(glBefore);
        assertThat(balanceMovement).isEqualByComparingTo("40.00");
        assertThat(glMovement).isEqualByComparingTo(balanceMovement);
    }

    @Test
    @DisplayName("same-currency transfer: one journal, two lines on 2001, nothing else")
    void transfer_sameCurrency() {
        UUID src = account("GL-TR-S", "USD", "300.00");
        UUID dst = account("GL-TR-D", "USD", "0.00");

        PaymentResponse p = paymentService.transfer(new TransferRequest(src, dst, new BigDecimal("120.00"), "it", null), "gl-it");

        assertThat(lines(p.id(), p.referenceNumber())).containsExactlyInAnyOrder(
                "DEBIT 2001 120.0000 USD", "CREDIT 2001 120.0000 USD");
        assertThat(transactionIds(p.id())).hasSize(1);
    }

    @Test
    @DisplayName("cross-currency transfer and its reversal: balanced per currency through the FX position accounts")
    void transfer_crossCurrency_andReversal() {
        UUID usd = account("GL-FX-U", "USD", "1000.00");
        UUID kes = account("GL-FX-K", "KES", "1000.00"); // above the demo product floor of 100

        PaymentResponse p = paymentService.transfer(new TransferRequest(usd, kes, new BigDecimal("100.00"), "it", null), "gl-it");

        BigDecimal credited = balance(kes).subtract(new BigDecimal("1000.00"));
        assertThat(credited).isPositive();
        List<String> lines = lines(p.id(), p.referenceNumber());
        assertThat(lines).contains("DEBIT 2001 100.0000 USD",
                "CREDIT 2001 " + credited.setScale(4) + " KES",
                "DEBIT 1200 " + credited.setScale(4) + " KES");
        assertThat(lines).anyMatch(l -> l.startsWith("CREDIT 1201 ") && l.endsWith(" USD"));
        assertBalancedPerCurrency(p.id());

        PaymentResponse rev = paymentService.reversePayment(p.id(), new ReversePaymentRequest("it"), "gl-it");

        // The destination gives back its own-currency amount, not the USD amount.
        assertThat(balance(kes)).isEqualByComparingTo("1000.00");
        assertThat(balance(usd)).isEqualByComparingTo("1000.00");
        assertThat(lines(rev.id(), rev.referenceNumber())).contains(
                "DEBIT 2001 " + credited.setScale(4) + " KES", "CREDIT 1200 " + credited.setScale(4) + " KES",
                "CREDIT 2001 100.0000 USD");
        assertBalancedPerCurrency(rev.id());
    }

    @Test
    @DisplayName("no GL mapping for Cash at Teller: the deposit is rejected and the balance is unchanged")
    void deposit_unmapped_isRejected() {
        UUID acc = account("GL-UNMAP", "USD", "100.00");
        UUID cashGl = jdbc.queryForObject(
                "SELECT gl_account_id FROM financial_activity_accounts WHERE financial_activity = 'ASSET_CASH_AT_TELLER'",
                UUID.class);
        jdbc.update("DELETE FROM financial_activity_accounts WHERE financial_activity = 'ASSET_CASH_AT_TELLER'");
        try {
            assertThatThrownBy(() -> accountService.deposit(acc, new BigDecimal("10.00"), "it", "gl-it"))
                    .isInstanceOf(CbaException.class).hasMessageContaining("ASSET_CASH_AT_TELLER");
            assertThat(balance(acc)).isEqualByComparingTo("100.00");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE account_id = ?", Integer.class, acc))
                    .isZero();
        } finally {
            jdbc.update("INSERT INTO financial_activity_accounts (financial_activity, gl_account_id) "
                    + "VALUES ('ASSET_CASH_AT_TELLER', ?)", cashGl);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID account(String number, String currency, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, ?, DATE '2025-01-01', 'gl-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(balance), currency);
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    /** "SIDE GLCODE AMOUNT CCY" for every line of the entity's journal with this reference. */
    private List<String> lines(UUID entityId, String reference) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND je.reference_number = ?""", String.class, entityId, reference);
    }

    private List<String> transactionIds(UUID entityId) {
        return jdbc.queryForList("SELECT DISTINCT transaction_id FROM journal_entries WHERE entity_id = ?",
                String.class, entityId);
    }

    private void assertBalancedPerCurrency(UUID entityId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT entry_type, amount, currency_code FROM journal_entries WHERE entity_id = ?", entityId)
                .forEach(r -> net.merge((String) r.get("currency_code"),
                        "DEBIT".equals(r.get("entry_type")) ? (BigDecimal) r.get("amount")
                                : ((BigDecimal) r.get("amount")).negate(), BigDecimal::add));
        assertThat(net).isNotEmpty();
        net.forEach((ccy, n) -> assertThat(n).as("net " + ccy).isEqualByComparingTo("0"));
    }

    /** Credit balance of GL 2001 Customer Deposits in one currency (credits − debits). */
    private BigDecimal depositsGlCredit(String currency) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = '2001' AND je.currency_code = ?""", BigDecimal.class, currency);
    }
}
