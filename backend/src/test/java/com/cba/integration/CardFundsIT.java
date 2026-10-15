package com.cba.integration;

import com.cba.account.AccountController;
import com.cba.account.AccountService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code GET /api/v1/accounts/{id}/balance} — card-service's debit-card balance check, over
 * real HTTP and PostgreSQL. card-service had always called this path; until cont. 35 it didn't
 * exist and every debit-card authorization declined RC 91.
 */
@DisplayName("Card funds — the debit-card balance check card-service calls")
class CardFundsIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountController accountController;
    @Autowired AccountService accountService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private UUID account(String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'card-funds-it')""",
                id, "CF-" + id.toString().substring(0, 8), DEMO_CUSTOMER, SAVINGS_PRODUCT, new BigDecimal(balance));
        return id;
    }

    @Test
    @DisplayName("200 with the ApiResponse envelope: spendable amount in the account's currency, no PII")
    void balance_envelope() throws Exception {
        UUID id = account("750.00");
        ResponseEntity<String> r = rest.getForEntity("/api/v1/accounts/" + id + "/balance", String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);

        JsonNode data = json.readTree(r.getBody()).get("data");
        assertThat(data.get("accountId").asText()).isEqualTo(id.toString());
        assertThat(data.get("currencyCode").asText()).isEqualTo("USD");
        assertThat(data.get("debitAllowed").asBoolean()).isTrue();
        assertThat(data.get("availableBalance").decimalValue())
                .isEqualByComparingTo(accountService.cardFunds(id).availableBalance())
                .isPositive();
        assertThat(data.has("customerName")).isFalse();
        assertThat(data.has("accountNumber")).isFalse();
    }

    @Test
    @DisplayName("a frozen account: debitAllowed=false, ACCOUNT_NOT_ACTIVE, 0")
    void frozen() throws Exception {
        UUID id = account("750.00");
        jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?", id);
        JsonNode data = json.readTree(rest.getForEntity("/api/v1/accounts/" + id + "/balance", String.class).getBody())
                .get("data");
        assertThat(data.get("debitAllowed").asBoolean()).isFalse();
        assertThat(data.get("reason").asText()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(data.get("availableBalance").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("unknown account: 404")
    void unknown() {
        assertThat(rest.getForEntity("/api/v1/accounts/" + UUID.randomUUID() + "/balance", String.class)
                .getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("CARD_SERVICE reaches this endpoint only: not the account itself; CUSTOMER can't reach it")
    void roles() {
        UUID id = account("100.00");

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("svc", null, "ROLE_CARD_SERVICE"));
        assertThat(accountController.getCardFunds(id).getBody().data().availableBalance()).isNotNull();
        assertThatThrownBy(() -> accountController.getAccount(id)).isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("c", null, "ROLE_CUSTOMER"));
        assertThatThrownBy(() -> accountController.getCardFunds(id)).isInstanceOf(AccessDeniedException.class);
    }
}
