package com.cba.integration;

import com.cba.accounting.OpeningBalanceService;
import com.cba.accounting.OpeningBalanceService.OpeningBalances;
import com.cba.accounting.OpeningBalanceService.OpeningLine;
import com.cba.accounting.OpeningBalanceService.Scope;
import com.cba.common.exception.CbaException;
import com.cba.loan.LoanAccrualService;
import com.cba.loan.LoanService;
import com.cba.loan.dto.LoanApplicationRequest;
import com.cba.loan.dto.LoanRepaymentRequest;
import com.cba.loan.dto.WaiveInterestRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * The LOANS opening-balance journal against a real ledger: a legacy loan (balances with
 * no GL history) is brought forward; after that, the sub-ledger and the GL stay equal
 * through accrual, a waiver (with its modification discount) and a repayment.
 *
 * <p>One test method: the journal can be posted only once per database, and the shared
 * test database holds other tests' loans, so assertions are invariants, not totals.
 */
@DisplayName("Opening-balance journal — legacy loan balances brought into the ledger")
class LoanOpeningBalanceIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID USD_LOAN_PRODUCT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired LoanService loanService;
    @Autowired LoanAccrualService loanAccrualService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("preview → post once → reconciled through accrual, waiver and repayment → second post rejected")
    void loanOpeningBalances() {
        LocalDate today = LocalDate.now();
        // The shared database already holds loans (demo data among them): measure what the legacy loan adds.
        OpeningBalances without = openingBalanceService.preview(Scope.LOANS);
        legacyLoan(today);

        OpeningBalances preview = openingBalanceService.preview(Scope.LOANS);
        assertThat(preview.posted()).isFalse();
        assertThat(preview.lines()).allSatisfy(l ->
                assertThat(l.openingAmount()).isEqualByComparingTo(l.customerBalance().subtract(l.ledgerBalance())));
        assertThat(added(without, preview, "1100")).as("legacy principal: a debit").isEqualByComparingTo("900.00");
        assertThat(added(without, preview, "1101")).as("legacy accrued interest").isEqualByComparingTo("9.00");
        assertThat(added(without, preview, "1105")).as("legacy allowance: a credit").isEqualByComparingTo("-12.50");

        Map<String, BigDecimal> clearingBefore = preview.clearingBalance();
        OpeningBalances posted = openingBalanceService.post(Scope.LOANS);

        assertThat(posted.posted()).isTrue();
        assertBalancedPerCurrency(posted.transactionId());
        Map<String, BigDecimal> openingByCurrency = new TreeMap<>();
        posted.lines().forEach(l -> openingByCurrency.merge(l.currency(), l.openingAmount(), BigDecimal::add));
        openingByCurrency.forEach((ccy, net) -> assertThat(posted.clearingBalance().getOrDefault(ccy, BigDecimal.ZERO)
                        .subtract(clearingBefore.getOrDefault(ccy, BigDecimal.ZERO)))
                .as("clearing offsets the %s openings", ccy).isEqualByComparingTo(net.negate()));
        assertReconciled();

        // After go-live every loan movement posts as it happens, so the ledger stays equal to the loans.
        UUID live = liveLoan();
        loanAccrualService.runCloseOfBusiness(live, today);
        assertReconciled();
        loanService.waiveInterest(live, new WaiveInterestRequest("hardship", WaiveInterestRequest.Scope.ALL, null, null));
        assertReconciled();
        loanService.makeRepayment(live, new LoanRepaymentRequest(new BigDecimal("100.00"), null, null, null, null));
        assertReconciled();

        assertThatThrownBy(() -> openingBalanceService.post(Scope.LOANS))
                .isInstanceOf(CbaException.class).hasMessageContaining("already been posted");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A loan from before GL posting: balances in the sub-ledger, nothing in the ledger. */
    private void legacyLoan(LocalDate today) {
        UUID loanId = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account(), new BigDecimal("1200.00"), 12, "legacy")).id();
        loanService.approveLoan(loanId, "legacy");
        jdbc.update("""
                UPDATE loans SET status = 'ACTIVE', outstanding_balance = 900.00, disbursement_date = ?,
                                 interest_accrued_through = ?, maturity_date = ?, loss_allowance = 12.50
                WHERE id = ?""", today.minusDays(20), today, today.plusDays(40), loanId);
        jdbc.update("""
                INSERT INTO loan_repayment_schedule (loan_id, installment_no, due_date, principal_due, interest_due,
                                                     total_due, interest_accrued)
                VALUES (?, 1, ?, 300.00, 15.00, 315.00, 9.00), (?, 2, ?, 600.00, 10.00, 610.00, 0)""",
                loanId, today.plusDays(10), loanId, today.plusDays(40));
    }

    /** A loan disbursed through the application (so posted), moved 40 days back. */
    private UUID liveLoan() {
        UUID loanId = loanService.applyForLoan(new LoanApplicationRequest(
                DEMO_CUSTOMER, USD_LOAN_PRODUCT, account(), new BigDecimal("1200.00"), 12, "live")).id();
        loanService.approveLoan(loanId, "it");
        loanService.disburseLoan(loanId);
        jdbc.update("""
                UPDATE loans SET disbursement_date = disbursement_date - 40,
                                 interest_accrued_through = interest_accrued_through - 40,
                                 maturity_date = maturity_date - 40
                WHERE id = ?""", loanId);
        jdbc.update("UPDATE loan_repayment_schedule SET due_date = due_date - 40 WHERE loan_id = ?", loanId);
        return loanId;
    }

    private void assertReconciled() {
        OpeningBalances after = openingBalanceService.preview(Scope.LOANS);
        assertThat(after.posted()).isTrue();
        assertThat(after.lines()).allSatisfy(l -> assertThat(l.openingAmount())
                .as("%s %s: ledger %s vs loans %s", l.glCode(), l.currency(), l.ledgerBalance(), l.customerBalance())
                .isEqualByComparingTo("0"));
    }

    /** How much the customer side of one USD line grew between two previews. */
    private static BigDecimal added(OpeningBalances before, OpeningBalances after, String glCode) {
        return customer(after, glCode).subtract(customer(before, glCode));
    }

    private static BigDecimal customer(OpeningBalances b, String glCode) {
        return b.lines().stream().filter(l -> l.glCode().equals(glCode) && l.currency().equals("USD"))
                .map(OpeningLine::customerBalance).findFirst().orElse(BigDecimal.ZERO);
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', 5000.00, 'USD', DATE '2024-01-01', 'loan-ob-it')""",
                id, "LN-OB-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT);
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
