package com.cba.integration;

import com.cba.account.AccountController;
import com.cba.account.AccountService;
import com.cba.account.dto.CardCaptureRequest;
import com.cba.account.dto.CardDebitRequest;
import com.cba.account.dto.CardHoldRequest;
import com.cba.account.dto.CardHoldResult;
import com.cba.cob.CobJobDefinition;
import com.cba.common.exception.CbaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Card clearing over real PostgreSQL (backlog 15a): a hold becomes a debit at clearing with
 * DR customer deposit / CR 2300 Card Scheme Settlement Payable; a single-message 0200 debits at
 * once; a reversal of a completed debit refunds it; expired holds are swept by the CoB job.
 */
@DisplayName("Card clearing — holds become debits, journals post to 2300")
class CardClearingIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired AccountService accountService;
    @Autowired AccountController accountController;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JobLauncher jobLauncher;
    @Autowired @Qualifier("holdExpiryBatchJob") Job holdExpiryJob;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    /** A USD savings account whose spendable amount is exactly {@code spendable} (product minimum balance added). */
    private UUID account(String spendable) {
        BigDecimal minimum = jdbc.queryForObject(
                "SELECT COALESCE(minimum_balance, 0) FROM deposit_products WHERE id = ?", BigDecimal.class, SAVINGS_PRODUCT);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'card-clearing-it')""",
                id, "CC-" + id.toString().substring(0, 8), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(spendable).add(minimum));
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private String holdStatus(String reference) {
        return jdbc.queryForObject("SELECT status FROM account_holds WHERE external_reference = ?", String.class, reference);
    }

    private void hold(UUID id, String ref, String amount) {
        assertThat(accountService.placeCardHold(id, new CardHoldRequest(ref, new BigDecimal(amount), "USD"), "it")
                .approved()).isTrue();
    }

    private CardHoldResult capture(UUID id, String ref, String amount) {
        return accountService.captureCardHold(id, ref, new CardCaptureRequest(new BigDecimal(amount), "USD"), "it");
    }

    /** "SIDE GLCODE AMOUNT CCY" for every card journal line of the account with this reference. */
    private List<String> lines(UUID account, String reference) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_type = 'CARD' AND je.entity_id = ? AND je.reference_number = ?
                ORDER BY je.entry_type, g.gl_code""", String.class, account, reference);
    }

    /** Net of the account's card journals per currency: zero when every journal balances. */
    private void assertCardJournalsBalanced(UUID account) {
        BigDecimal net = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END), 0)
                FROM journal_entries WHERE entity_type = 'CARD' AND entity_id = ?""", BigDecimal.class, account);
        assertThat(net).isEqualByComparingTo("0");
    }

    private long cardJournalLines(UUID account) {
        return jdbc.queryForObject("SELECT count(*) FROM journal_entries WHERE entity_type = 'CARD' AND entity_id = ?",
                Long.class, account);
    }

    @Test
    @DisplayName("clearing: the hold becomes a debit; DR deposit / CR 2300 posts, balanced")
    void capture_debitsAndPosts() {
        UUID id = account("500.00");
        BigDecimal before = balance(id);
        hold(id, "CARD-C-1", "100.00");
        assertThat(accountService.cardFunds(id).availableBalance()).isEqualByComparingTo("400.00");

        CardHoldResult r = capture(id, "CARD-C-1", "100.00");

        assertThat(r.approved()).as(r.reason()).isTrue();
        assertThat(r.status()).isEqualTo("CAPTURED");
        assertThat(balance(id)).isEqualByComparingTo(before.subtract(new BigDecimal("100.00")));
        assertThat(accountService.cardFunds(id).availableBalance()).isEqualByComparingTo("400.00");
        List<String> journal = lines(id, "CARD-C-1");
        assertThat(journal).contains("CREDIT 2300 100.0000 USD");
        assertThat(journal).anyMatch(l -> l.startsWith("DEBIT ") && l.endsWith(" 100.0000 USD"));
        assertCardJournalsBalanced(id);
        assertThat(jdbc.queryForObject("SELECT transaction_type FROM transactions WHERE account_id = ? AND reference_number = ?",
                String.class, id, "CARD-C-1")).isEqualTo("CARD_PURCHASE");
    }

    @Test
    @DisplayName("partial clearing debits the presented amount and frees the rest of the hold")
    void partialCapture() {
        UUID id = account("500.00");
        hold(id, "CARD-P-1", "100.00");
        assertThat(capture(id, "CARD-P-1", "60.00").approved()).isTrue();
        assertThat(accountService.cardFunds(id).availableBalance()).isEqualByComparingTo("440.00");
        assertThat(lines(id, "CARD-P-1")).contains("CREDIT 2300 60.0000 USD");
    }

    @Test
    @DisplayName("idempotent: the same clearing again debits nothing more; another amount is REFERENCE_CONFLICT")
    void captureIdempotent() {
        UUID id = account("500.00");
        BigDecimal before = balance(id);
        hold(id, "CARD-I-1", "100.00");
        assertThat(capture(id, "CARD-I-1", "100.00").approved()).isTrue();
        assertThat(capture(id, "CARD-I-1", "100.0000").approved()).isTrue();
        CardHoldResult other = capture(id, "CARD-I-1", "90.00");
        assertThat(other.approved()).isFalse();
        assertThat(other.reason()).isEqualTo("REFERENCE_CONFLICT");
        assertThat(balance(id)).isEqualByComparingTo(before.subtract(new BigDecimal("100.00")));
        assertThat(cardJournalLines(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("refused: released hold, more than authorized, unknown reference, other currency — nothing posts")
    void captureRefusals() {
        UUID id = account("500.00");
        hold(id, "CARD-X-1", "100.00");
        assertThat(capture(id, "CARD-X-1", "100.01").reason()).isEqualTo("AMOUNT_EXCEEDS_HOLD");
        assertThat(accountService.captureCardHold(id, "CARD-X-1", new CardCaptureRequest(new BigDecimal("10"), "EUR"), "it")
                .reason()).isEqualTo("CURRENCY_MISMATCH");
        assertThat(capture(id, "CARD-NOPE", "10.00").reason()).isEqualTo("HOLD_NOT_FOUND");
        accountService.releaseCardHold(id, "CARD-X-1", "it");
        assertThat(capture(id, "CARD-X-1", "100.00").reason()).isEqualTo("HOLD_RELEASED");
        assertThat(cardJournalLines(id)).isZero();
    }

    @Test
    @DisplayName("a live hold clears even if the account was frozen after the authorization (funds were reserved)")
    void liveHold_clearsOnFrozenAccount() {
        UUID id = account("500.00");
        hold(id, "CARD-F-1", "100.00");
        jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?", id);
        assertThat(capture(id, "CARD-F-1", "100.00").approved()).isTrue();
    }

    @Test
    @DisplayName("late presentment (hold expired): debits under the withdrawal rules, or is refused — never force-posted")
    void latePresentment() {
        UUID ok = account("500.00");
        hold(ok, "CARD-L-1", "100.00");
        jdbc.update("UPDATE account_holds SET expiry_date = CURRENT_DATE - 1 WHERE external_reference = 'CARD-L-1'");
        assertThat(capture(ok, "CARD-L-1", "100.00").approved()).isTrue();

        UUID frozen = account("500.00");
        hold(frozen, "CARD-L-2", "100.00");
        jdbc.update("UPDATE account_holds SET expiry_date = CURRENT_DATE - 1 WHERE external_reference = 'CARD-L-2'");
        jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?", frozen);
        CardHoldResult refused = capture(frozen, "CARD-L-2", "100.00");
        assertThat(refused.approved()).isFalse();
        assertThat(refused.reason()).isEqualTo("ACCOUNT_NOT_ACTIVE");

        UUID spent = account("500.00");
        hold(spent, "CARD-L-3", "400.00");
        jdbc.update("UPDATE account_holds SET expiry_date = CURRENT_DATE - 1 WHERE external_reference = 'CARD-L-3'");
        hold(spent, "CARD-L-4", "300.00");   // the expired hold no longer reserved those funds
        assertThat(capture(spent, "CARD-L-3", "400.00").reason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(cardJournalLines(frozen) + cardJournalLines(spent)).isZero();
    }

    @Test
    @DisplayName("0200: card-debits debits at once (cash), idempotent; a reversal refunds DR 2300 / CR deposit")
    void singleMessageDebit_andRefund() {
        UUID id = account("500.00");
        BigDecimal before = balance(id);
        CardDebitRequest atm = new CardDebitRequest("CARD-ATM-1", new BigDecimal("200.00"), "USD", true);

        assertThat(accountService.cardDebit(id, atm, "it").approved()).isTrue();
        assertThat(accountService.cardDebit(id, atm, "it").approved()).isTrue();   // retry
        assertThat(balance(id)).isEqualByComparingTo(before.subtract(new BigDecimal("200.00")));
        assertThat(jdbc.queryForObject("SELECT transaction_type FROM transactions WHERE account_id = ? AND reference_number = ?",
                String.class, id, "CARD-ATM-1")).isEqualTo("CARD_CASH_WITHDRAWAL");
        assertThat(accountService.cardDebit(id,
                new CardDebitRequest("CARD-ATM-2", new BigDecimal("400.00"), "USD", true), "it").reason())
                .isEqualTo("INSUFFICIENT_FUNDS");

        CardHoldResult refund = accountService.releaseCardHold(id, "CARD-ATM-1", "it");
        assertThat(refund.approved()).isTrue();
        assertThat(refund.status()).isEqualTo("REFUNDED");
        assertThat(accountService.releaseCardHold(id, "CARD-ATM-1", "it").approved()).isTrue();   // retry
        assertThat(balance(id)).isEqualByComparingTo(before);
        assertThat(lines(id, "CARD-ATM-1")).contains("CREDIT 2300 200.0000 USD", "DEBIT 2300 200.0000 USD");
        assertCardJournalsBalanced(id);
        assertThat(accountService.cardDebit(id, atm, "it").reason()).isEqualTo("HOLD_RELEASED");
    }

    @Test
    @DisplayName("a reversal after clearing refunds the cleared amount")
    void releaseAfterCapture_refunds() {
        UUID id = account("500.00");
        BigDecimal before = balance(id);
        hold(id, "CARD-RC-1", "100.00");
        capture(id, "CARD-RC-1", "80.00");
        assertThat(accountService.releaseCardHold(id, "CARD-RC-1", "it").status()).isEqualTo("REFUNDED");
        assertThat(balance(id)).isEqualByComparingTo(before);
        assertCardJournalsBalanced(id);
    }

    @Test
    @DisplayName("with 2300 unmapped the clearing is rejected and nothing changes (no unposted debit)")
    void unmappedActivity_rejects() {
        UUID id = account("500.00");
        BigDecimal before = balance(id);
        hold(id, "CARD-U-1", "100.00");
        UUID glAccount = jdbc.queryForObject(
                "SELECT gl_account_id FROM financial_activity_accounts WHERE financial_activity = 'LIABILITY_CARD_SETTLEMENT'",
                UUID.class);
        jdbc.update("DELETE FROM financial_activity_accounts WHERE financial_activity = 'LIABILITY_CARD_SETTLEMENT'");
        try {
            assertThatThrownBy(() -> capture(id, "CARD-U-1", "100.00"))
                    .isInstanceOf(CbaException.class).hasMessageContaining("LIABILITY_CARD_SETTLEMENT");
        } finally {
            jdbc.update("INSERT INTO financial_activity_accounts (financial_activity, gl_account_id) "
                    + "VALUES ('LIABILITY_CARD_SETTLEMENT', ?)", glAccount);
        }
        assertThat(balance(id)).isEqualByComparingTo(before);
        assertThat(holdStatus("CARD-U-1")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("CoB hold expiry: ACTIVE holds past their date become EXPIRED; live and captured holds stay")
    void holdExpiryJob() throws Exception {
        UUID id = account("500.00");
        hold(id, "CARD-EX-1", "50.00");
        hold(id, "CARD-EX-2", "50.00");
        hold(id, "CARD-EX-3", "50.00");
        capture(id, "CARD-EX-3", "50.00");
        jdbc.update("UPDATE account_holds SET expiry_date = CURRENT_DATE - 1 WHERE external_reference IN ('CARD-EX-1','CARD-EX-3')");

        var execution = jobLauncher.run(holdExpiryJob, CobJobDefinition.parameters(LocalDate.now()));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(holdStatus("CARD-EX-1")).isEqualTo("EXPIRED");
        assertThat(holdStatus("CARD-EX-2")).isEqualTo("ACTIVE");
        assertThat(holdStatus("CARD-EX-3")).isEqualTo("CAPTURED");
    }

    @Test
    @DisplayName("over HTTP: capture and card-debits return the ApiResponse envelope")
    void http() throws Exception {
        UUID id = account("500.00");
        hold(id, "CARD-H-1", "120.50");
        ResponseEntity<String> captured = rest.postForEntity("/api/v1/accounts/" + id + "/card-holds/CARD-H-1/capture",
                Map.of("amount", 120.50, "currencyCode", "USD"), String.class);
        assertThat(captured.getStatusCode().value()).as(captured.getBody()).isEqualTo(200);
        JsonNode data = json.readTree(captured.getBody()).get("data");
        assertThat(data.get("status").asText()).isEqualTo("CAPTURED");

        ResponseEntity<String> debited = rest.postForEntity("/api/v1/accounts/" + id + "/card-debits",
                Map.of("reference", "CARD-H-2", "amount", 20, "currencyCode", "USD", "cashWithdrawal", true), String.class);
        assertThat(json.readTree(debited.getBody()).get("data").get("approved").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("only CARD_SERVICE (and ADMIN) may clear or debit: TELLER and CUSTOMER are refused")
    void roles() {
        UUID id = account("100.00");
        hold(id, "CARD-S-1", "10.00");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("svc", null, "ROLE_CARD_SERVICE"));
        assertThat(accountController.captureCardHold(id, "CARD-S-1",
                new CardCaptureRequest(new BigDecimal("10.00"), "USD"), null).getBody().data().approved()).isTrue();
        for (String role : new String[]{"ROLE_TELLER", "ROLE_CUSTOMER"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, role));
            assertThatThrownBy(() -> accountController.cardDebit(id,
                    new CardDebitRequest("CARD-S-2", new BigDecimal("1.00"), "USD", false), null))
                    .as(role).isInstanceOf(AccessDeniedException.class);
        }
    }
}
