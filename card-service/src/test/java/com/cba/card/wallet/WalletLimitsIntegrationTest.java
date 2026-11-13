package com.cba.card.wallet;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.common.CbaException;
import com.cba.card.integration.AbstractCardIntegrationTest;
import com.cba.card.settlement.BackendClearingClient;
import com.cba.card.settlement.BackendClearingClient.PrepaidPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Wallet load limits (backlog 15c, decision D4a) against real PostgreSQL: the per-card advisory lock makes
 * concurrent loads see each other, so loads that together exceed a limit can't all pass — and money coming
 * back (a reversal) is credited even when it takes the wallet over the maximum (CBN 2012 §3.7).
 * The backend is a Mockito bean that approves every load.
 */
@DisplayName("Wallet limits — concurrent loads serialised, reversals never refused (real PostgreSQL)")
class WalletLimitsIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID PREPAID_PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000003");

    @MockitoBean BackendClearingClient backend;

    @Autowired WalletService walletService;
    @Autowired CardService cardService;
    @Autowired PrepaidWalletRepository walletRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate rest;

    private final UUID account = UUID.randomUUID();

    @BeforeEach
    void approveEveryLoad() {
        when(backend.prepaidLoad(any(), any(), any(), any(), any(), any()))
                .thenAnswer(i -> new PrepaidPost(true, false, i.getArgument(0), null, "GL-IT", null, null, null, null, null));
    }

    @Test
    @DisplayName("NGN (default 50,000 a day): eight concurrent first loads of 30,000 → exactly one COMPLETED, seven DAILY_LOAD_LIMIT")
    void concurrentFirstLoadsDailyLimit() throws Exception {
        Card card = card(PREPAID_PRODUCT);

        List<Object> outcomes = race(8, () -> walletService.load(card.getId(), account, new BigDecimal("30000"), "NGN", "it"));

        assertThat(outcomes).filteredOn(o -> o instanceof WalletMovement m && WalletMovement.COMPLETED.equals(m.getStatus())).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof CbaException e && "DAILY_LOAD_LIMIT".equals(e.getCode())).hasSize(7);
        assertThat(balance(card)).isEqualByComparingTo("30000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wallet_movements WHERE card_id = ?", Integer.class, card.getId()))
                .isEqualTo(1);   // a refused load claims nothing

        WalletService.WalletLimits limits = walletService.wallet(card.getId()).limits();
        assertThat(limits.maxBalance()).isEqualByComparingTo("250000");
        assertThat(limits.dailyLoad()).isEqualByComparingTo("50000");
        assertThat(limits.loadedToday()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("product maximum 100 USD: two concurrent loads of 60 → one COMPLETED, one WALLET_BALANCE_LIMIT")
    void concurrentLoadsBalanceLimit() throws Exception {
        Card card = card(product(new BigDecimal("100"), null));
        walletService.load(card.getId(), account, new BigDecimal("1"), "USD", "it");   // open the wallet

        List<Object> outcomes = race(2, () -> walletService.load(card.getId(), account, new BigDecimal("60"), null, "it"));

        assertThat(outcomes).filteredOn(o -> o instanceof WalletMovement).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof CbaException e && "WALLET_BALANCE_LIMIT".equals(e.getCode())).hasSize(1);
        assertThat(balance(card)).isEqualByComparingTo("61");
    }

    @Test
    @DisplayName("a reversal that takes the wallet over its maximum is still credited; the next load is refused")
    void reversalOverTheLimitSucceeds() {
        String pan = pan();
        Card card = cardService.issueCard(product(new BigDecimal("100"), null), UUID.randomUUID(), null, true, pan, "2912", "123", "USD");
        walletService.load(card.getId(), account, new BigDecimal("100"), "USD", "it");

        assertThat(authorize(pan, "870001", 2500)).isEqualTo("00");
        assertThat(balance(card)).isEqualByComparingTo("75");
        walletService.load(card.getId(), account, new BigDecimal("25"), null, "it");   // back at the maximum
        assertThat(balance(card)).isEqualByComparingTo("100");

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 2500, "stan", "870001",
                "originalDataElements", "0100870001")).get("responseCode")).isEqualTo("00");
        assertThat(balance(card)).isEqualByComparingTo("125");

        assertThatThrownBy(() -> walletService.load(card.getId(), account, new BigDecimal("0.01"), null, "it"))
                .hasFieldOrPropertyWithValue("code", "WALLET_BALANCE_LIMIT");
        assertThat(walletService.wallet(card.getId()).limits().dailyLoad()).isNull();   // USD: no default
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Runs {@code n} copies of {@code call} at once; each outcome is the result or the exception. */
    private static List<Object> race(int n, Callable<Object> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return call.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> f : futures) outcomes.add(f.get(60, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID product(BigDecimal maxBalance, BigDecimal dailyLoad) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO card_products (id, name, card_type, bin_range_start, bin_range_end, max_wallet_balance, daily_wallet_load) "
                + "VALUES (?, ?, 'PREPAID', '43000000', '43999999', ?, ?)", id, "Limits IT " + id, maxBalance, dailyLoad);
        return id;
    }

    private Card card(UUID product) {
        return cardService.issueCard(product, UUID.randomUUID(), null, true, pan(), "2912", "123", "USD");
    }

    private static String pan() {
        return "4300" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    private BigDecimal balance(Card card) {
        return walletRepository.findByCardId(card.getId()).orElseThrow().getBalance();
    }

    private String authorize(String pan, String stan, long amountMinor) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("pan", pan), Map.entry("processingCode", "000000"), Map.entry("amount", amountMinor),
                Map.entry("currencyCode", "840"), Map.entry("stan", stan), Map.entry("terminalId", "TERM0003"),
                Map.entry("merchantId", "M" + stan), Map.entry("merchantName", "Shop " + stan),
                Map.entry("mcc", "5411"), Map.entry("posEntryMode", "051"), Map.entry("rrn", "8123" + stan + "00"),
                Map.entry("scheme", "VISA"), Map.entry("pinVerified", true), Map.entry("arqcValid", true),
                Map.entry("isFinancial", false));
        return (String) post("/api/v1/internal/authorize", body).get("responseCode");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".FEP_SERVICE");
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return r.getBody();
    }
}
