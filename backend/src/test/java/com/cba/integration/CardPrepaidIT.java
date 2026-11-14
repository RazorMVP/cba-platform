package com.cba.integration;

import com.cba.account.CardInterchangeDirection;
import com.cba.accounting.OpeningBalanceService;
import com.cba.accounting.PrepaidWalletTotals;
import com.cba.cardprepaid.CardPrepaidService;
import com.cba.cardprepaid.dto.PrepaidClearingRequest;
import com.cba.cardprepaid.dto.PrepaidMovementResult;
import com.cba.cardprepaid.dto.PrepaidRefundRequest;
import com.cba.cardprepaid.dto.PrepaidTransferRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Backlog 15c against real PostgreSQL: prepaid wallet loads and unloads move the customer's deposit to and from
 * GL 2500 Prepaid Card Liabilities; cleared prepaid spend moves 2500 to 2300 with interchange and fees; refunds
 * reverse it pro rata; the PREPAID opening balance brings card-service's wallets onto 2500.
 */
@DisplayName("Prepaid card wallets on the GL (backlog 15c)")
class CardPrepaidIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @MockitoBean PrepaidWalletTotals walletTotals;

    @Autowired CardPrepaidService service;
    @Autowired OpeningBalanceService openingBalanceService;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private final UUID card = UUID.randomUUID();

    @Test
    @DisplayName("load: DR 2001 / CR 2500, account debited; the same reference again is a repeat, not a second debit")
    void load() {
        UUID acc = account("PP-L", "500.00");
        PrepaidTransferRequest req = new PrepaidTransferRequest("WLD-" + UUID.randomUUID(), DEMO_CUSTOMER, card, acc,
                new BigDecimal("120.00"), "USD");

        PrepaidMovementResult r = service.load(req, "card-service");

        assertThat(r.approved()).as(r.reason()).isTrue();
        assertThat(balance(acc)).isEqualByComparingTo("380.00");
        assertThat(lines(req.reference())).containsExactlyInAnyOrder("DEBIT 2001 120.0000 USD", "CREDIT 2500 120.0000 USD");
        assertThat(service.load(req, "card-service").repeat()).isTrue();
        assertThat(balance(acc)).isEqualByComparingTo("380.00");
        assertThat(service.load(new PrepaidTransferRequest(req.reference(), DEMO_CUSTOMER, card, acc, new BigDecimal("1.00"), "USD"),
                "card-service").reason()).isEqualTo("REFERENCE_CONFLICT");
    }

    @Test
    @DisplayName("load refusals: someone else's account, wrong currency, not enough funds — nothing moves")
    void loadRefusals() {
        UUID acc = account("PP-R", "150.00");   // the demo product keeps a 100.00 minimum
        assertThat(service.load(new PrepaidTransferRequest("WLD-" + UUID.randomUUID(), UUID.randomUUID(), card, acc,
                BigDecimal.TEN, "USD"), "cs").reason()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(service.load(new PrepaidTransferRequest("WLD-" + UUID.randomUUID(), DEMO_CUSTOMER, card, acc,
                BigDecimal.TEN, "EUR"), "cs").reason()).isEqualTo("CURRENCY_MISMATCH");
        assertThat(service.load(new PrepaidTransferRequest("WLD-" + UUID.randomUUID(), DEMO_CUSTOMER, card, acc,
                new BigDecimal("60.00"), "USD"), "cs").reason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(balance(acc)).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("clearing: DR 2500 / CR 2300, interchange earned DR 2300 / CR 4005, fees DR 5006 / CR 2300; refund reverses pro rata")
    void clearingAndRefund() {
        String ref = "PPD-" + UUID.randomUUID();
        PrepaidMovementResult c = service.clearing(new PrepaidClearingRequest(ref, card, new BigDecimal("100.00"), "USD",
                "VISA", new BigDecimal("1.50"), CardInterchangeDirection.EARNED, new BigDecimal("0.20")), "cs");

        assertThat(c.approved()).as(c.reason()).isTrue();
        assertThat(lines(ref)).containsExactlyInAnyOrder(
                "DEBIT 2500 100.0000 USD", "CREDIT 2300 100.0000 USD",
                "DEBIT 2300 1.5000 USD", "CREDIT 4005 1.5000 USD",
                "DEBIT 5006 0.2000 USD", "CREDIT 2300 0.2000 USD");

        String refundRef = "REV-" + UUID.randomUUID();
        PrepaidMovementResult r = service.refund(ref, new PrepaidRefundRequest(refundRef, new BigDecimal("40.00"), "USD"), "cs");
        assertThat(r.approved()).as(r.reason()).isTrue();
        assertThat(r.interchangeReversed()).isEqualByComparingTo("0.60");
        assertThat(r.schemeFeeReversed()).isEqualByComparingTo("0.08");
        assertThat(lines(refundRef)).containsExactlyInAnyOrder(
                "CREDIT 2500 40.0000 USD", "DEBIT 2300 40.0000 USD",
                "CREDIT 2300 0.6000 USD", "DEBIT 4005 0.6000 USD",
                "CREDIT 5006 0.0800 USD", "DEBIT 2300 0.0800 USD");

        assertThat(service.refund(ref, new PrepaidRefundRequest(refundRef, new BigDecimal("40.00"), "USD"), "cs").repeat()).isTrue();
        assertThat(service.refund(ref, new PrepaidRefundRequest("REV-" + UUID.randomUUID(), new BigDecimal("60.01"), "USD"), "cs")
                .reason()).isEqualTo("AMOUNT_EXCEEDS_CLEARED");
        assertThat(service.refund("PPD-none", new PrepaidRefundRequest("REV-" + UUID.randomUUID(), BigDecimal.ONE, "USD"), "cs").reason())
                .isEqualTo("CLEARING_NOT_FOUND");
    }

    @Test
    @DisplayName("15d (D1a): two partial refunds of one clearing reverse its fees exactly; a third over what's left is refused")
    void partialRefunds_reverseFeesExactly() {
        String ref = "PPD-" + UUID.randomUUID();
        assertThat(service.clearing(new PrepaidClearingRequest(ref, card, new BigDecimal("100.00"), "USD",
                "VISA", new BigDecimal("1.50"), CardInterchangeDirection.EARNED, new BigDecimal("0.13")), "cs").approved()).isTrue();

        String first = "REV-" + UUID.randomUUID();
        PrepaidMovementResult a = service.refund(ref, new PrepaidRefundRequest(first, new BigDecimal("33.33"), "USD"), "cs");
        String second = "REV-" + UUID.randomUUID();
        PrepaidMovementResult b = service.refund(ref, new PrepaidRefundRequest(second, new BigDecimal("66.67"), "USD"), "cs");

        assertThat(a.approved()).as(a.reason()).isTrue();
        assertThat(b.approved()).as(b.reason()).isTrue();
        assertThat(a.interchangeReversed()).isEqualByComparingTo("0.50");   // 1.50 × 33.33 / 100 = 0.49995 → 0.50
        assertThat(a.schemeFeeReversed()).isEqualByComparingTo("0.04");     // 0.0433 → 0.04
        assertThat(b.interchangeReversed()).isEqualByComparingTo("1.00");   // 1.50 − 0.50
        assertThat(b.schemeFeeReversed()).isEqualByComparingTo("0.09");     // 0.13 − 0.04
        assertThat(jdbc.queryForObject("SELECT refunded_amount FROM prepaid_card_movements WHERE reference = ?",
                BigDecimal.class, ref)).isEqualByComparingTo("100.00");
        for (String gl : new String[]{"2300", "2500", "4005", "5006"}) {
            assertThat(net(List.of(ref, first, second), gl)).as(gl).isEqualByComparingTo("0");
        }
        assertThat(service.refund(ref, new PrepaidRefundRequest("REV-" + UUID.randomUUID(), new BigDecimal("0.01"), "USD"), "cs")
                .reason()).isEqualTo("AMOUNT_EXCEEDS_CLEARED");
        assertThat(service.refund(ref, new PrepaidRefundRequest(first, new BigDecimal("33.33"), "USD"), "cs").repeat()).isTrue();
    }

    /** Net (debits − credits) of a GL account over the journals with these references. */
    private BigDecimal net(List<String> references, String glCode) {
        BigDecimal total = BigDecimal.ZERO;
        for (String reference : references) {
            total = total.add(jdbc.queryForObject("""
                    SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                    FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                    WHERE je.reference_number = ? AND g.gl_code = ?""", BigDecimal.class, reference, glCode));
        }
        return total;
    }

    @Test
    @DisplayName("unload: DR 2500 / CR 2001, account credited")
    void unload() {
        UUID acc = account("PP-U", "200.00");
        String ref = "WUL-" + UUID.randomUUID();
        PrepaidMovementResult r = service.unload(new PrepaidTransferRequest(ref, DEMO_CUSTOMER, card, acc,
                new BigDecimal("25.00"), "USD"), "cs");
        assertThat(r.approved()).as(r.reason()).isTrue();
        assertThat(balance(acc)).isEqualByComparingTo("225.00");
        assertThat(lines(ref)).containsExactlyInAnyOrder("DEBIT 2500 25.0000 USD", "CREDIT 2001 25.0000 USD");
    }

    @Test
    @DisplayName("only the card-service role posts: an ADMIN (dev bypass) gets 403")
    void staffCantPost() {
        assertThat(rest.postForEntity("/api/v1/card-prepaid/clearings", Map.of("reference", "x"), String.class)
                .getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("PREPAID opening balance: card-service's wallets minus what 2500 already holds, against 3900; card-service down → 503")
    void openingBalance() {
        BigDecimal usdOnLedger = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'DEBIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = '2500' AND je.currency_code = 'USD'""", BigDecimal.class);
        // USD wallets equal what the ledger holds (nothing to bring forward); GHS wallets were never posted.
        when(walletTotals.totals()).thenReturn(Optional.of(Map.of("USD", usdOnLedger.negate(), "GHS", new BigDecimal("123.45"))));

        var preview = openingBalanceService.preview(OpeningBalanceService.Scope.PREPAID);
        var ghs = preview.lines().stream().filter(l -> l.currency().equals("GHS")).findFirst().orElseThrow();
        assertThat(ghs.glCode()).isEqualTo("2500");
        assertThat(ghs.openingAmount()).isEqualByComparingTo("-123.45");
        assertThat(preview.lines().stream().filter(l -> l.currency().equals("USD")).findFirst().orElseThrow().openingAmount())
                .isEqualByComparingTo("0");

        var posted = openingBalanceService.post(OpeningBalanceService.Scope.PREPAID);
        assertThat(posted.posted()).isTrue();
        List<String> journal = jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.transaction_id = ?""", String.class, posted.transactionId());
        assertThat(journal).containsExactlyInAnyOrder("CREDIT 2500 123.4500 GHS", "DEBIT 3900 123.4500 GHS");

        when(walletTotals.totals()).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> openingBalanceService.preview(OpeningBalanceService.Scope.PREPAID))
                .hasFieldOrPropertyWithValue("errorCode", "PREPAID_TOTALS_UNAVAILABLE");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID account(String number, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'pp-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT, new BigDecimal(balance));
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private List<String> lines(String reference) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.reference_number = ?""", String.class, reference);
    }
}
