package com.cba.integration;

import com.cba.account.AccountController;
import com.cba.account.AccountService;
import com.cba.account.dto.CardHoldRequest;
import com.cba.account.dto.CardHoldResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Card holds over real PostgreSQL: a card approval reserves its amount atomically, so two
 * authorizations can't both spend the same balance (the double-spend the #154 security review
 * found: approvals reserved nothing).
 */
@DisplayName("Card holds — atomic check-and-hold for card authorizations")
class CardHoldsIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired AccountService accountService;
    @Autowired AccountController accountController;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

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
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'card-holds-it')""",
                id, "CH-" + id.toString().substring(0, 8), DEMO_CUSTOMER, SAVINGS_PRODUCT,
                new BigDecimal(spendable).add(minimum));
        return id;
    }

    private static CardHoldRequest hold(String ref, String amount) {
        return new CardHoldRequest(ref, new BigDecimal(amount), "USD");
    }

    @Test
    @DisplayName("double-spend: two concurrent 400.00 authorizations on 500.00 → exactly one reserved")
    void concurrentAuthorizations_oneWins() throws Exception {
        UUID id = account("500.00");
        CountDownLatch start = new CountDownLatch(1);
        Callable<CardHoldResult> a = () -> { start.await(); return accountService.placeCardHold(id, hold("CARD-A-1", "400.00"), "it"); };
        Callable<CardHoldResult> b = () -> { start.await(); return accountService.placeCardHold(id, hold("CARD-B-1", "400.00"), "it"); };
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<CardHoldResult>> futures = List.of(pool.submit(a), pool.submit(b));
            start.countDown();
            long approved = 0;
            for (Future<CardHoldResult> f : futures) if (f.get().approved()) approved++;
            assertThat(approved).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM account_holds WHERE account_id = ? AND status = 'ACTIVE'",
                BigDecimal.class, id)).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("a reversal frees the funds; the same reference can't hold again after release")
    void releaseFreesFunds_andReferenceIsSpent() {
        UUID id = account("500.00");
        assertThat(accountService.placeCardHold(id, hold("CARD-R-1", "400.00"), "it").approved()).isTrue();
        assertThat(accountService.placeCardHold(id, hold("CARD-R-2", "400.00"), "it").reason()).isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(accountService.releaseCardHold(id, "CARD-R-1", "it").approved()).isTrue();
        assertThat(accountService.placeCardHold(id, hold("CARD-R-2", "400.00"), "it").approved()).isTrue();

        CardHoldResult retry = accountService.placeCardHold(id, hold("CARD-R-1", "400.00"), "it");
        assertThat(retry.approved()).isFalse();
        assertThat(retry.reason()).isEqualTo("HOLD_RELEASED");
    }

    @Test
    @DisplayName("an expired hold no longer reduces the balance")
    void expiredHoldIgnored() {
        UUID id = account("500.00");
        assertThat(accountService.placeCardHold(id, hold("CARD-E-1", "400.00"), "it").approved()).isTrue();
        jdbc.update("UPDATE account_holds SET expiry_date = CURRENT_DATE - 1 WHERE external_reference = 'CARD-E-1'");
        assertThat(accountService.cardFunds(id).availableBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("over HTTP: POST card-holds and release return the ApiResponse envelope")
    void http() throws Exception {
        UUID id = account("500.00");
        ResponseEntity<String> placed = rest.postForEntity("/api/v1/accounts/" + id + "/card-holds",
                Map.of("reference", "CARD-H-1", "amount", 120.50, "currencyCode", "USD"), String.class);
        assertThat(placed.getStatusCode().value()).as(placed.getBody()).isEqualTo(200);
        JsonNode data = json.readTree(placed.getBody()).get("data");
        assertThat(data.get("approved").asBoolean()).isTrue();
        assertThat(data.get("availableBalance").decimalValue()).isEqualByComparingTo("379.50");

        ResponseEntity<String> released = rest.postForEntity("/api/v1/accounts/" + id + "/card-holds/CARD-H-1/release",
                null, String.class);
        assertThat(json.readTree(released.getBody()).get("data").get("status").asText()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("only CARD_SERVICE (and ADMIN) may reserve: TELLER and CUSTOMER are refused")
    void roles() {
        UUID id = account("100.00");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("svc", null, "ROLE_CARD_SERVICE"));
        assertThat(accountController.placeCardHold(id, hold("CARD-S-1", "10.00"), null).getBody().data().approved()).isTrue();
        for (String role : new String[]{"ROLE_TELLER", "ROLE_CUSTOMER"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, role));
            assertThatThrownBy(() -> accountController.placeCardHold(id, hold("CARD-S-2", "10.00"), null))
                    .as(role).isInstanceOf(AccessDeniedException.class);
        }
    }
}
