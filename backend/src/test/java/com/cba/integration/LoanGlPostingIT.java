package com.cba.integration;

import com.cba.charge.ChargeDefinition;
import com.cba.charge.ChargeService;
import com.cba.charge.LoanCharge;
import com.cba.common.exception.CbaException;
import com.cba.loan.LoanService;
import com.cba.loan.LoanStatus;
import com.cba.loan.dto.ForecloseRequest;
import com.cba.loan.dto.ForeclosureQuote;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.LoanRepaymentResponse;
import com.cba.loan.dto.LoanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Every loan money movement posts a balanced journal against the GL accounts V8/V54/V57
 * seed: disbursement, repayment from an account, a charge recognised and paid, and a
 * foreclosure settled in teller cash. At the end the loan portfolio (1100) nets to zero for
 * the loan, and the till holds the cash it received.
 */
@DisplayName("GL posting — loans and loan charges against a real database")
class LoanGlPostingIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired LoanService loanService;
    @Autowired ChargeService chargeService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("disburse → repay from account → penalty charged and paid → foreclose in teller cash")
    void loanLifecycle_postsEveryMovement() {
        UUID account = account("LN-GL", "500.00");
        UUID loanId = approvedLoan(account, "1200.00");

        // 1. Disbursement: DR 1100 loan portfolio / CR 2001 customer deposits.
        loanService.disburseLoan(loanId);
        assertThat(balance(account)).isEqualByComparingTo("1700.00");
        assertThat(lines(loanId, "DISB-%")).containsExactlyInAnyOrder(
                "DEBIT 1100 1200.0000 USD", "CREDIT 2001 1200.0000 USD");

        // 2. Repayment from the linked account clears principal and interest receivable.
        LoanRepaymentResponse r = loanService.makeRepayment(loanId,
                new LoanRepaymentRequest(new BigDecimal("150.00"), null, null, null, null));
        assertThat(balance(account)).isEqualByComparingTo("1550.00");
        List<String> repayment = lines(loanId, "RPMT-%");
        assertThat(repayment).contains("DEBIT 2001 150.0000 USD",
                "CREDIT 1100 " + r.principalPortion().setScale(4) + " USD");
        if (r.interestPortion().signum() > 0) {
            assertThat(repayment).contains("CREDIT 1101 " + r.interestPortion().setScale(4) + " USD");
        }

        // 3. A penalty is income when charged; paying it clears the receivable.
        ChargeDefinition penalty = chargeService.createCharge(new ChargeService.CreateChargeRequest(
                "IT late fee " + UUID.randomUUID().toString().substring(0, 6), "USD",
                ChargeDefinition.ChargeAppliesTo.LOAN, ChargeDefinition.ChargeTimeType.OVERDUE_INSTALLMENT,
                ChargeDefinition.ChargeCalculation.FLAT, new BigDecimal("25.00"), true, true));
        LoanCharge charge = chargeService.addLoanCharge(loanId,
                new ChargeService.AddChargeRequest(penalty.getId(), null, null));
        assertThat(charge.getIncomeRecognizedOn()).isEqualTo(LocalDate.now());
        assertThat(lines(loanId, "CHG-%")).containsExactlyInAnyOrder(
                "DEBIT 1103 25.0000 USD", "CREDIT 4004 25.0000 USD");
        chargeService.payLoanCharge(loanId, charge.getId(), null);
        assertThat(balance(account)).isEqualByComparingTo("1525.00");
        assertThat(lines(loanId, "CHGP-%")).containsExactlyInAnyOrder(
                "DEBIT 2001 25.0000 USD", "CREDIT 1103 25.0000 USD");

        // 4. Foreclosure in cash: the till records it and Cash at Teller takes the debit.
        UUID session = openTellerSession();
        ForeclosureQuote quote = loanService.getForeclosureQuote(loanId, null);
        assertThat(quote.principal()).isEqualByComparingTo(new BigDecimal("1200.00").subtract(r.principalPortion()));
        LoanResponse closed = loanService.forecloseLoan(loanId,
                new ForecloseRequest(null, "early settlement", "CASH", null, session));
        assertThat(closed.status()).isEqualTo(LoanStatus.FORECLOSED);
        assertThat(lines(loanId, "FCLS-%")).contains(
                "DEBIT 1001 " + quote.total().setScale(4) + " USD",
                "CREDIT 1100 " + quote.principal().setScale(4) + " USD");
        assertThat(jdbc.queryForObject(
                "SELECT amount FROM cash_transactions WHERE loan_id = ? AND session_id = ?",
                BigDecimal.class, loanId, session)).isEqualByComparingTo(quote.total());
        assertThat(balance(account)).as("cash foreclosure leaves the account alone").isEqualByComparingTo("1525.00");

        // Every journal balances, and the portfolio is back to zero for this loan.
        assertBalancedPerJournal(loanId);
        assertThat(netDebit(loanId, "1100")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("overpayment is rejected and nothing leaves the account")
    void overpayment_rejected() {
        UUID account = account("LN-OVR", "500.00");
        UUID loanId = approvedLoan(account, "600.00");
        loanService.disburseLoan(loanId);

        assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                new LoanRepaymentRequest(new BigDecimal("5000.00"), null, null, null, null)))
                .isInstanceOf(CbaException.class).hasMessageContaining("exceeds");
        assertThat(balance(account)).isEqualByComparingTo("1100.00");
        assertThat(lines(loanId, "RPMT-%")).isEmpty();
    }

    @Test
    @DisplayName("a repayment the account cannot cover is rejected with no journal")
    void insufficientFunds_rejected() {
        UUID account = account("LN-NSF", "100.00");
        UUID loanId = approvedLoan(account, "600.00");
        loanService.disburseLoan(loanId);
        jdbc.update("UPDATE accounts SET balance = 100.00 WHERE id = ?", account); // spend the proceeds

        assertThatThrownBy(() -> loanService.makeRepayment(loanId,
                new LoanRepaymentRequest(new BigDecimal("60.00"), null, null, null, null)))
                .isInstanceOf(CbaException.class);
        assertThat(lines(loanId, "RPMT-%")).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID approvedLoan(UUID account, String principal) {
        LoanResponse applied = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account, new BigDecimal(principal), 12, "it"));
        loanService.approveLoan(applied.id(), "it");
        return applied.id();
    }

    private UUID account(String number, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'loan-gl-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(balance));
        return id;
    }

    /** An open USD till: teller → cashier → today's session. */
    private UUID openTellerSession() {
        UUID teller = UUID.randomUUID(), cashier = UUID.randomUUID(), session = UUID.randomUUID();
        jdbc.update("INSERT INTO tellers (id, name, branch_code, status) VALUES (?, ?, '001', 'ACTIVE')",
                teller, "IT till " + teller.toString().substring(0, 6));
        jdbc.update("INSERT INTO cashiers (id, teller_id, staff_id) VALUES (?, ?, 'it-cashier')", cashier, teller);
        jdbc.update("""
                INSERT INTO teller_sessions (id, teller_id, cashier_id, opening_balance, currency_code, status)
                VALUES (?, ?, ?, 1000.00, 'USD', 'OPEN')""", session, teller, cashier);
        return session;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    /** "SIDE GLCODE AMOUNT CCY" for the loan's journal lines whose reference matches. */
    private List<String> lines(UUID loanId, String referencePattern) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_type = 'LOAN' AND je.entity_id = ? AND je.reference_number LIKE ?""",
                String.class, loanId, referencePattern);
    }

    /** Debits minus credits on one GL code for the loan. */
    private BigDecimal netDebit(UUID loanId, String glCode) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND g.gl_code = ?""", BigDecimal.class, loanId, glCode);
    }

    /** Each journal (transaction_id) of the loan nets to zero in every currency. */
    private void assertBalancedPerJournal(UUID loanId) {
        Map<String, BigDecimal> net = new TreeMap<>();
        jdbc.queryForList("SELECT transaction_id, entry_type, amount, currency_code FROM journal_entries WHERE entity_id = ?",
                loanId).forEach(row -> net.merge(row.get("transaction_id") + "/" + row.get("currency_code"),
                "DEBIT".equals(row.get("entry_type")) ? (BigDecimal) row.get("amount")
                        : ((BigDecimal) row.get("amount")).negate(), BigDecimal::add));
        assertThat(net).hasSizeGreaterThanOrEqualTo(5);
        net.forEach((k, n) -> assertThat(n).as("net " + k).isEqualByComparingTo("0"));
    }
}
