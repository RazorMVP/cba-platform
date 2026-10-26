package com.cba.integration;

import com.cba.cardsettlement.CardSchemeSettlementController;
import com.cba.cardsettlement.CardSchemeSettlementService;
import com.cba.cardsettlement.dto.CardSchemeSettlementRequest;
import com.cba.cardsettlement.dto.CardSchemeSettlementResult;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cash leg of card scheme settlement over real PostgreSQL (backlog 15b): card-service posts a
 * scheme's net settlement once its settlement report matched — DR 2300 / CR 1003 when the bank paid,
 * the reverse when the scheme paid; one journal per reference.
 */
@DisplayName("Card scheme settlement — the cash leg posts once per reference")
class CardSchemeSettlementIT extends AbstractIntegrationTest {

    @Autowired CardSchemeSettlementService service;
    @Autowired CardSchemeSettlementController controller;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static String ref() {
        return "CSR-IT-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private CardSchemeSettlementResult post(String reference, String amount) {
        return service.record(new CardSchemeSettlementRequest(reference, "VISA", LocalDate.now().minusDays(1), "USD",
                new BigDecimal(amount)), "it");
    }

    private List<String> lines(String reference) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_type = 'CARD' AND je.reference_number = ?
                ORDER BY je.entry_type, g.gl_code""", String.class, reference);
    }

    @Test
    @DisplayName("the bank paid the scheme: DR 2300 / CR 1003, dated today, settlement recorded")
    void positiveNet() {
        String ref = ref();
        CardSchemeSettlementResult r = post(ref, "149.20");

        assertThat(r.approved()).as(r.reason()).isTrue();
        assertThat(r.repeat()).isFalse();
        assertThat(r.journalTransactionId()).isNotBlank();
        assertThat(lines(ref)).containsExactly("CREDIT 1003 149.2000 USD", "DEBIT 2300 149.2000 USD");
        assertThat(jdbc.queryForObject("SELECT DISTINCT transaction_date FROM journal_entries WHERE reference_number = ?",
                LocalDate.class, ref)).isEqualTo(LocalDate.now());
        assertThat(jdbc.queryForObject("SELECT scheme || ' ' || amount FROM card_scheme_settlements WHERE reference = ?",
                String.class, ref)).isEqualTo("VISA 149.2000");
    }

    @Test
    @DisplayName("the scheme paid the bank (refunds outweighed spend): DR 1003 / CR 2300")
    void negativeNet() {
        String ref = ref();
        assertThat(post(ref, "-39.45").approved()).isTrue();
        assertThat(lines(ref)).containsExactly("CREDIT 2300 39.4500 USD", "DEBIT 1003 39.4500 USD");
    }

    @Test
    @DisplayName("the same reference twice posts one journal; other figures under it are refused")
    void idempotentOnReference() {
        String ref = ref();
        CardSchemeSettlementResult first = post(ref, "10.00");
        CardSchemeSettlementResult again = post(ref, "10.00");
        CardSchemeSettlementResult other = post(ref, "10.01");

        assertThat(again.approved()).isTrue();
        assertThat(again.repeat()).isTrue();
        assertThat(again.journalTransactionId()).isEqualTo(first.journalTransactionId());
        assertThat(other.approved()).isFalse();
        assertThat(other.reason()).isEqualTo("REFERENCE_CONFLICT");
        assertThat(lines(ref)).hasSize(2);
    }

    @Test
    @DisplayName("two concurrent requests with one reference: exactly one journal")
    void concurrentSameReference() throws Exception {
        String ref = ref();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<CardSchemeSettlementResult> call = () -> post(ref, "25.00");
            List<Future<CardSchemeSettlementResult>> results = pool.invokeAll(List.of(call, call));
            for (Future<CardSchemeSettlementResult> f : results) {
                assertThat(f.get().approved()).isTrue();
            }
            assertThat(results.stream().filter(f -> {
                try { return f.get().repeat(); } catch (Exception e) { throw new IllegalStateException(e); }
            }).count()).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(lines(ref)).hasSize(2);
    }

    @Test
    @DisplayName("refusals post nothing: zero, too many decimals, unknown currency, unknown scheme, a date not over yet")
    void refusals() {
        String scheme = ref();
        assertThat(service.record(new CardSchemeSettlementRequest(scheme, "ACME", LocalDate.now().minusDays(1), "USD",
                BigDecimal.TEN), "it").reason()).isEqualTo("UNKNOWN_SCHEME");
        String today = ref();
        assertThat(service.record(new CardSchemeSettlementRequest(today, "VISA", LocalDate.now(), "USD",
                BigDecimal.TEN), "it").reason()).isEqualTo("DATE_NOT_CLOSED");
        assertThat(lines(scheme)).isEmpty();
        assertThat(lines(today)).isEmpty();
        String zero = ref();
        assertThat(post(zero, "0.00").reason()).isEqualTo("ZERO_AMOUNT");
        String decimals = ref();
        assertThat(post(decimals, "1.005").reason()).isEqualTo("INVALID_AMOUNT");
        String currency = ref();
        assertThat(service.record(new CardSchemeSettlementRequest(currency, "VISA", LocalDate.now().minusDays(1), "XYZ",
                BigDecimal.TEN), "it").reason()).isEqualTo("UNKNOWN_CURRENCY");
        assertThat(lines(zero)).isEmpty();
        assertThat(lines(decimals)).isEmpty();
        assertThat(lines(currency)).isEmpty();
    }

    @Test
    @DisplayName("the settlement-bank account unmapped: rejected, nothing recorded")
    void unmapped1003_rejects() {
        String ref = ref();
        UUID gl = jdbc.queryForObject("SELECT gl_account_id FROM financial_activity_accounts "
                + "WHERE financial_activity = 'ASSET_CARD_SCHEME_SETTLEMENT'", UUID.class);
        jdbc.update("DELETE FROM financial_activity_accounts WHERE financial_activity = 'ASSET_CARD_SCHEME_SETTLEMENT'");
        try {
            assertThatThrownBy(() -> post(ref, "5.00"))
                    .isInstanceOf(CbaException.class).hasMessageContaining("ASSET_CARD_SCHEME_SETTLEMENT");
        } finally {
            jdbc.update("INSERT INTO financial_activity_accounts (financial_activity, gl_account_id) "
                    + "VALUES ('ASSET_CARD_SCHEME_SETTLEMENT', ?)", gl);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card_scheme_settlements WHERE reference = ?",
                Integer.class, ref)).isZero();
    }

    @Test
    @DisplayName("only CARD_SERVICE may post — not ADMIN (it would skip card-service's reconciliation), TELLER or CUSTOMER")
    void roles() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("svc", null, "ROLE_CARD_SERVICE"));
        assertThat(controller.record(new CardSchemeSettlementRequest(ref(), "VISA", LocalDate.now().minusDays(1),
                "USD", BigDecimal.ONE), null).getBody().data().approved()).isTrue();
        for (String role : new String[]{"ROLE_ADMIN", "ROLE_TELLER", "ROLE_CUSTOMER"}) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, role));
            assertThatThrownBy(() -> controller.record(new CardSchemeSettlementRequest(ref(), "VISA",
                    LocalDate.now().minusDays(1), "USD", BigDecimal.ONE), null))
                    .as(role).isInstanceOf(AccessDeniedException.class);
        }
    }
}
