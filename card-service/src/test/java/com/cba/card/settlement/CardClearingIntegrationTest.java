package com.cba.card.settlement;

import com.cba.card.card.Card;
import com.cba.card.card.CardService;
import com.cba.card.integration.AbstractCardIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Card clearing end to end over real HTTP and PostgreSQL (backlog 15a), with a WireMock container
 * playing the backend: a debit purchase (0100) is held and lands in the batch PENDING; batch close
 * clears it with {@code POST …/card-holds/{ref}/capture} (major units, alphabetic currency); an ATM
 * 0200 debits at once through {@code …/card-debits} and its reversal refunds through {@code …/release}.
 */
@DisplayName("Card clearing — approvals fill the batch, close captures on the backend (WireMock)")
class CardClearingIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID DEBIT_PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000001");
    static final String ACCOUNT = "cccccccc-0000-0000-0000-00000000c1e4";

    @SuppressWarnings("resource")
    static final GenericContainer<?> BACKEND =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    static {
        BACKEND.start();
    }

    private static final RestTemplate ADMIN = new RestTemplate();

    private static String backendUrl() {
        return "http://" + BACKEND.getHost() + ":" + BACKEND.getMappedPort(8080);
    }

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        registry.add("backend.base-url", CardClearingIntegrationTest::backendUrl);
    }

    @BeforeAll
    static void stubs() {
        String accounts = "/api/v1/accounts/" + ACCOUNT;
        register("POST", accounts + "/card-holds", hold(true, "ACTIVE", null));
        register("POST", accounts + "/card-debits", hold(true, "CAPTURED", null));
        registerPattern("POST", accounts + "/card-holds/[^/]+/capture", hold(true, "CAPTURED", null));
        registerPattern("POST", accounts + "/card-holds/[^/]+/release", hold(true, "REFUNDED", null));
        register("POST", "/api/v1/card-settlements", Map.of("data", Map.of("approved", true, "repeat", false,
                "reference", "CSR", "journalTransactionId", "GL-IT"), "errors", List.of()));
        // The backend owns the tolerance (15d): a reported 149.30 against 149.20 is over it.
        priorityMapping(Map.of("method", "POST", "urlPath", "/api/v1/card-settlements",
                        "bodyPatterns", List.of(Map.of("contains", "\"amount\":149.30"))),
                Map.of("data", Map.of("approved", false, "repeat", false, "reference", "CSR",
                        "reason", "DIFFERENCE_OVER_TOLERANCE", "expectedAmount", 149.20, "amount", 149.30,
                        "difference", 0.10), "errors", List.of()));
        Map<String, Object> prepaidOk = Map.of("data", Map.of("approved", true, "repeat", false, "reference", "P",
                "journalTransactionId", "GL-PP"), "errors", List.of());
        register("POST", "/api/v1/card-prepaid/loads", prepaidOk);
        register("POST", "/api/v1/card-prepaid/clearings", prepaidOk);
        registerPattern("POST", "/api/v1/card-prepaid/clearings/[^/]+/refund", prepaidOk);
    }

    static final UUID PREPAID_PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000003");

    @Test
    @DisplayName("prepaid (15c): load over HTTP → wallet credited; a purchase lands PENDING under PPD-; close clears it on the backend")
    void prepaidLoadSpendClear() {
        String pan = "4300" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        Card card = cardService.issueCard(PREPAID_PRODUCT, UUID.randomUUID(), null, true, pan, "2912", "123", "USD");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".ADMIN.TELLER");
        var load = rest.exchange("/api/v1/cards/" + card.getId() + "/wallet/loads", HttpMethod.POST,
                new HttpEntity<>(Map.of("sourceAccountId", ACCOUNT, "amount", 100, "currencyCode", "USD"), h), Map.class);
        assertThat(load.getStatusCode().value()).as(String.valueOf(load.getBody())).isEqualTo(201);
        assertThat(requests("/card-prepaid/loads")).anySatisfy(body -> assertThat(body)
                .contains(card.getId().toString()).contains("\"currencyCode\":\"USD\""));

        assertThat(authorize(pan, "930001", "000000", false, 2500)).isEqualTo("00");
        SettlementItem item = itemFor("930001");
        assertThat(item.getStatus()).isEqualTo("PENDING");
        assertThat(item.getHoldReference()).startsWith("PPD-");
        assertThat(jdbc.queryForObject("SELECT balance FROM prepaid_wallets WHERE card_id = ?", BigDecimal.class, card.getId()))
                .isEqualByComparingTo("75.00");

        settlementService.closeBatch(batchOf(item));

        SettlementItem cleared = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(cleared.getStatus()).isEqualTo("SETTLED");
        assertThat(cleared.getGlNet()).isNotNull();   // in the scheme's expected net from now on
        assertThat(requests("/card-prepaid/clearings")).anySatisfy(body -> assertThat(body).contains(item.getHoldReference()));
    }

    @Autowired TestRestTemplate rest;
    @Autowired CardService cardService;
    @Autowired SettlementService settlementService;
    @Autowired SchemeSettlementService schemeSettlementService;
    @Autowired SettlementItemRepository itemRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("0100 → PENDING item; batch close captures it (25.00 USD) → SETTLED; batch CLOSED")
    void purchaseClearedAtBatchClose() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");

        assertThat(authorize(pan, "910001", "000000", false, 2500)).isEqualTo("00");
        SettlementItem item = itemFor("910001");
        assertThat(item.getStatus()).isEqualTo("PENDING");
        assertThat(item.getAccountId()).isEqualTo(UUID.fromString(ACCOUNT));
        assertThat(item.getHoldReference()).startsWith("CARD-");

        SettlementBatch closed = settlementService.closeBatch(batchOf(item));

        assertThat(closed.getStatus()).isEqualTo(SettlementBatchStatus.CLOSED);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo("SETTLED");
        assertThat(requests("/card-holds/" + item.getHoldReference() + "/capture"))
                .anySatisfy(body -> assertThat(body).contains("\"currencyCode\":\"USD\"").contains("25"));
    }

    @Test
    @DisplayName("0200 ATM cash debits at once (card-debits, cash=true) → item SETTLED; a 0400 refunds it → REVERSED")
    void atmCashDebitedAndRefunded() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");

        assertThat(authorize(pan, "920001", "010000", true, 4000)).isEqualTo("00");
        SettlementItem item = itemFor("920001");
        assertThat(item.getStatus()).isEqualTo("SETTLED");
        assertThat(requests("/card-debits")).anySatisfy(body -> assertThat(body).contains("\"cashWithdrawal\":true"));

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 4000, "stan", "920001",
                "originalDataElements", "0200920001")).get("responseCode")).isEqualTo("00");
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo("REVERSED");
        assertThat(requests("/card-holds/" + item.getHoldReference() + "/release")).isNotEmpty();
    }

    @Test
    @DisplayName("full reversal the backend refuses → 13; the REVERSED mark rolls back, the purchase still clears")
    void refusedFullReversalLeavesItemPending() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");

        assertThat(authorize(pan, "930001", "000000", false, 3000)).isEqualTo("00");
        SettlementItem item = itemFor("930001");
        // The most recently added WireMock stub wins: this hold's release is refused.
        register("POST", "/api/v1/accounts/" + ACCOUNT + "/card-holds/" + item.getHoldReference() + "/release",
                hold(false, "CAPTURED", "AMOUNT_EXCEEDS_CAPTURED"));

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 3000, "stan", "930001",
                "originalDataElements", "0100930001")).get("responseCode")).isEqualTo("13");
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authorization_log WHERE stan = '930001' AND mti = '0400'",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("15b: the capture carries the issuer's interchange + fees (Visa debit MCC 5411, chip) and the item keeps glNet")
    void captureCarriesInterchangeAndFees() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");
        assertThat(authorize(pan, "940001", "000000", false, 2500)).isEqualTo("00");
        SettlementItem item = itemFor("940001");
        assertThat(item.getScheme()).isEqualTo("VISA");
        // V3 seed: VISA DEBIT 5411 card present 0.05 % + 0.22 → 0.0125 + 0.22 = 0.23; fees 0.11 % + 0.0195 → 0.05.
        Map<String, Object> posted = new java.util.HashMap<>(hold(true, "CAPTURED", null));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = new java.util.HashMap<>((Map<String, Object>) posted.get("data"));
        data.put("interchangeAmount", 0.23);
        data.put("interchangeDirection", "EARNED");
        data.put("schemeFeeAmount", 0.05);
        posted.put("data", data);
        register("POST", "/api/v1/accounts/" + ACCOUNT + "/card-holds/" + item.getHoldReference() + "/capture", posted);

        settlementService.closeBatch(batchOf(item));

        assertThat(requests("/card-holds/" + item.getHoldReference() + "/capture")).anySatisfy(body -> assertThat(body)
                .contains("\"interchangeAmount\":0.23").contains("\"interchangeDirection\":\"EARNED\"")
                .contains("\"schemeFeeAmount\":0.05").contains("\"scheme\":\"VISA\""));
        SettlementItem cleared = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(cleared.getStatus()).isEqualTo("SETTLED");
        assertThat(cleared.getGlNet()).isEqualByComparingTo("2482");   // 2500 − 23 + 5
    }

    @Test
    @DisplayName("15b/15d: a difference over the backend's tolerance is undone; within it the report is recorded once "
            + "with its difference and the batch settles")
    void schemeReportPostsOnceAndSettles() {
        // A closed batch of an earlier day (no other test uses this date), two cleared Visa USD items.
        java.time.LocalDate day = java.time.LocalDate.of(2001, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
        UUID batch = UUID.randomUUID();
        jdbc.update("INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, closed_at) VALUES (?, ?, 'CLOSED', ?, now())",
                batch, batch.toString(), day);
        for (String glNet : new String[]{"9863", "5057"}) {
            jdbc.update("INSERT INTO settlement_items (batch_id, amount, currency_code, status, card_type, scheme, gl_net, cleared_at) "
                    + "VALUES (?, ?, '840', 'SETTLED', 'DEBIT', 'VISA', ?, now())", batch, new BigDecimal(glNet), new BigDecimal(glNet));
        }
        int before = requests("/api/v1/card-settlements").size();

        SchemeReportResult over = schemeSettlementService.record(new SchemeReportRequest("VISA", day, "USD", new BigDecimal("149.30")), "it");
        assertThat(over.recorded()).isFalse();
        assertThat(over.reason()).isEqualTo("DIFFERENCE_OVER_TOLERANCE");
        assertThat(over.expectedNet()).isEqualByComparingTo("149.20");
        assertThat(over.difference()).isEqualByComparingTo("0.10");
        assertThat(requests("/api/v1/card-settlements")).hasSize(before + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheme_settlement_reports WHERE settlement_date = ?",
                Integer.class, day)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement_items WHERE batch_id = ? AND settlement_report_id IS NOT NULL",
                Integer.class, batch)).isZero();

        SchemeReportResult right = schemeSettlementService.record(new SchemeReportRequest("VISA", day, "840", new BigDecimal("149.21")), "it");
        assertThat(right.recorded()).as(right.reason()).isTrue();
        assertThat(right.journalTransactionId()).isEqualTo("GL-IT");
        assertThat(right.difference()).isEqualByComparingTo("0.01");
        SchemeReportResult again = schemeSettlementService.record(new SchemeReportRequest("VISA", day, "USD", new BigDecimal("149.21")), "it");
        assertThat(again.repeat()).isTrue();

        List<String> calls = requests("/api/v1/card-settlements");
        assertThat(calls).hasSize(before + 2);
        assertThat(calls).anySatisfy(body -> assertThat(body).contains("\"amount\":149.21").contains("\"expectedAmount\":149.20")
                .contains("\"currencyCode\":\"USD\"").contains("\"scheme\":\"VISA\""));
        assertThat(jdbc.queryForObject("SELECT difference FROM scheme_settlement_reports WHERE settlement_date = ?",
                BigDecimal.class, day)).isEqualByComparingTo("0.01");
        assertThat(jdbc.queryForObject("SELECT status FROM settlement_batches WHERE id = ?", String.class, batch)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement_items WHERE batch_id = ? AND settlement_report_id IS NOT NULL",
                Integer.class, batch)).isEqualTo(2);
    }

    @Test
    @DisplayName("15d (D1a): two partial reversals of one completed debit (own STANs, DE90 names the original) → both RC 00, "
            + "two refund rows; the item's refunded amount is their total")
    void twoPartialReversalsOfDebit() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");
        assertThat(authorize(pan, "950001", "010000", true, 10000)).isEqualTo("00");
        SettlementItem item = itemFor("950001");
        assertThat(item.getStatus()).isEqualTo("SETTLED");

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 3000, "stan", "950002",
                "originalDataElements", "0200950001")).get("responseCode")).isEqualTo("00");
        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 7000, "stan", "950003",
                "originalDataElements", "0200950001")).get("responseCode")).isEqualTo("00");
        // A retried second reversal (same STAN) is the 0400 already recorded: nothing more.
        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 7000, "stan", "950003",
                "originalDataElements", "0200950001")).get("responseCode")).isEqualTo("00");

        List<Map<String, Object>> refunds = jdbc.queryForList(
                "SELECT reversal_reference, amount_minor, refund_settlement_date FROM settlement_refunds WHERE item_id = ? "
                        + "ORDER BY created_at", item.getId());
        assertThat(refunds).hasSize(2);
        assertThat(refunds).extracting(r -> ((BigDecimal) r.get("amount_minor")).stripTrailingZeros().toPlainString())
                .containsExactly("3000", "7000");
        assertThat(refunds).extracting(r -> (String) r.get("reversal_reference")).allMatch(k -> k.startsWith("REV-"))
                .doesNotHaveDuplicates();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getRefundedAmount()).isEqualByComparingTo("10000");
        assertThat(requests("/card-holds/" + item.getHoldReference() + "/release")).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains("\"amount\":30"))
                .anySatisfy(body -> assertThat(body).contains("\"amount\":70"));
    }

    @Test
    @DisplayName("15d (D1a): prepaid — two partial reversals after clearing → two backend refunds, the wallet credited twice")
    void twoPartialReversalsOfPrepaid() {
        String pan = "4300" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        Card card = cardService.issueCard(PREPAID_PRODUCT, UUID.randomUUID(), null, true, pan, "2912", "123", "USD");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".ADMIN.TELLER");
        assertThat(rest.exchange("/api/v1/cards/" + card.getId() + "/wallet/loads", HttpMethod.POST,
                new HttpEntity<>(Map.of("sourceAccountId", ACCOUNT, "amount", 100, "currencyCode", "USD"), h), Map.class)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(authorize(pan, "960001", "000000", false, 4000)).isEqualTo("00");
        SettlementItem item = itemFor("960001");
        settlementService.closeBatch(batchOf(item));
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getGlNet()).isNotNull();
        assertThat(wallet(card)).isEqualByComparingTo("60.00");

        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 1000, "stan", "960002",
                "originalDataElements", "0100960001")).get("responseCode")).isEqualTo("00");
        assertThat(post("/api/v1/internal/reverse", Map.of("pan", pan, "amount", 500, "stan", "960003",
                "originalDataElements", "0100960001")).get("responseCode")).isEqualTo("00");

        assertThat(wallet(card)).isEqualByComparingTo("75.00");
        assertThat(requests("/clearings/" + item.getHoldReference() + "/refund")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement_refunds WHERE item_id = ?", Integer.class, item.getId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("15d (D1a): refunds count on their own day's expected net; recording a report stamps that day's refund rows only")
    void refundsLandOnTheirOwnDay() {
        java.time.LocalDate d1 = java.time.LocalDate.of(2001, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(3000));
        java.time.LocalDate d2 = d1.minusDays(9000);
        UUID batch = UUID.randomUUID();
        jdbc.update("INSERT INTO settlement_batches (id, batch_ref, status, settlement_date, closed_at) VALUES (?, ?, 'CLOSED', ?, now())",
                batch, batch.toString(), d2);
        UUID itemId = UUID.randomUUID();
        jdbc.update("INSERT INTO settlement_items (id, batch_id, amount, currency_code, status, card_type, scheme, gl_net, cleared_at) "
                + "VALUES (?, ?, 10000, '840', 'SETTLED', 'DEBIT', 'VISA', 9863, now())", itemId, batch);
        // Two refunds of the one item, on two different days.
        jdbc.update("INSERT INTO settlement_refunds (item_id, reversal_reference, amount_minor, refund_gl_net, refund_settlement_date) "
                + "VALUES (?, ?, 3000, -2955, ?)", itemId, "REV-" + UUID.randomUUID(), d1);
        jdbc.update("INSERT INTO settlement_refunds (item_id, reversal_reference, amount_minor, refund_gl_net, refund_settlement_date) "
                + "VALUES (?, ?, 1000, -985, ?)", itemId, "REV-" + UUID.randomUUID(), d1.plusDays(1));

        assertThat(schemeSettlementService.expected("VISA", d1, "USD").expectedNet()).isEqualByComparingTo("-29.55");
        assertThat(schemeSettlementService.expected("VISA", d1.plusDays(1), "USD").expectedNet()).isEqualByComparingTo("-9.85");
        assertThat(schemeSettlementService.expected("VISA", d2, "USD").expectedNet()).isEqualByComparingTo("98.63");

        SchemeReportResult r = schemeSettlementService.record(new SchemeReportRequest("VISA", d1, "USD", new BigDecimal("-29.55")), "it");
        assertThat(r.recorded()).as(r.reason()).isTrue();
        assertThat(r.refundCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement_refunds WHERE item_id = ? AND refund_report_id IS NOT NULL",
                Integer.class, itemId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT refund_settlement_date FROM settlement_refunds WHERE item_id = ? AND refund_report_id IS NULL",
                java.time.LocalDate.class, itemId)).isEqualTo(d1.plusDays(1));
    }

    @Test
    @DisplayName("15d bound: prepaid approval with no settlement item (pre-V12) — reversals via DE90 give back at most the "
            + "original; one over what is left → RC 13; one without an amount gives the rest; the wallet gets it once in total")
    void prepaidWithoutItem_reversalsBoundedByOriginal() {
        Card card = loadedPrepaid("100");
        String pan = panOf(card);
        assertThat(authorize(pan, "970001", "000000", false, 4000)).isEqualTo("00");
        UUID auth = approvedAuth("970001");
        jdbc.update("DELETE FROM settlement_items WHERE authorization_log_id = ?", auth);   // as before V12: no item
        assertThat(wallet(card)).isEqualByComparingTo("60.00");

        assertThat(reverse(pan, 3000L, "970002", "0100970001")).isEqualTo("00");
        assertThat(wallet(card)).isEqualByComparingTo("90.00");
        assertThat(reverse(pan, 4000L, "970003", "0100970001")).isEqualTo("13");   // only 1000 left
        assertThat(reverse(pan, null, "970004", "0100970001")).isEqualTo("00");    // no amount: the rest
        assertThat(reverse(pan, 1L, "970005", "0100970001")).isEqualTo("13");      // nothing left
        assertThat(reverse(pan, 3000L, "970002", "0100970001")).isEqualTo("00");   // retry of the first: no-op

        assertThat(wallet(card)).isEqualByComparingTo("100.00");   // 40.00 back in total, exactly once
        assertThat(reversedAmount(auth)).isEqualByComparingTo("4000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM authorization_log WHERE card_id = ? AND mti = '0400'",
                Integer.class, card.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("15d bound: debit pending hold — a partial reversal lowers the item, one without an amount releases the "
            + "rest of the hold once, a further one → RC 13")
    void debitPending_reversalsBoundedByOriginal() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");
        assertThat(authorize(pan, "980001", "000000", false, 10000)).isEqualTo("00");
        UUID auth = approvedAuth("980001");
        SettlementItem item = itemRepository.findByAuthorizationLogId(auth).orElseThrow();

        assertThat(reverse(pan, 6000L, "980002", "0100980001")).isEqualTo("00");
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getAmount()).isEqualByComparingTo("4000");
        assertThat(requests("/card-holds/" + item.getHoldReference() + "/release")).isEmpty();   // hold kept for the rest
        assertThat(reverse(pan, 10000L, "980003", "0100980001")).isEqualTo("13");   // more than is left
        assertThat(reverse(pan, null, "980004", "0100980001")).isEqualTo("00");     // the rest: hold released
        assertThat(reverse(pan, 1L, "980005", "0100980001")).isEqualTo("13");

        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus()).isEqualTo("REVERSED");
        assertThat(requests("/card-holds/" + item.getHoldReference() + "/release")).hasSize(1)
                .allSatisfy(body -> assertThat(body).contains("\"amount\":40"));
        assertThat(reversedAmount(auth)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("15d bound: six concurrent reversals of one original (own STANs, DE90) — the original's lock lets three "
            + "through; the wallet gets 90.00 of the 100.00, never more")
    void concurrentReversals_neverExceedOriginal() throws Exception {
        Card card = loadedPrepaid("100");
        String pan = panOf(card);
        assertThat(authorize(pan, "990001", "000000", false, 10000)).isEqualTo("00");
        UUID auth = approvedAuth("990001");
        jdbc.update("DELETE FROM settlement_items WHERE authorization_log_id = ?", auth);
        assertThat(wallet(card)).isEqualByComparingTo("0.00");

        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String stan = "99010" + i;
            results.add(pool.submit(() -> {
                start.await();
                return reverse(pan, 3000L, stan, "0100990001");
            }));
        }
        start.countDown();
        List<String> codes = new java.util.ArrayList<>();
        for (var f : results) codes.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(codes).containsOnly("00", "13");
        assertThat(codes.stream().filter("00"::equals).count()).isEqualTo(3);
        assertThat(wallet(card)).isEqualByComparingTo("90.00");
        assertThat(reversedAmount(auth)).isEqualByComparingTo("9000");
    }

    /** A prepaid card whose wallet was loaded with {@code major} USD over HTTP (the backend is WireMock). */
    private Card loadedPrepaid(String major) {
        String pan = "4300" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        Card card = cardService.issueCard(PREPAID_PRODUCT, UUID.randomUUID(), null, true, pan, "2912", "123", "USD");
        pans.put(card.getId(), pan);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".ADMIN.TELLER");
        assertThat(rest.exchange("/api/v1/cards/" + card.getId() + "/wallet/loads", HttpMethod.POST,
                new HttpEntity<>(Map.of("sourceAccountId", ACCOUNT, "amount", new BigDecimal(major), "currencyCode", "USD"), h),
                Map.class).getStatusCode().value()).isEqualTo(201);
        return card;
    }

    private final java.util.Map<UUID, String> pans = new java.util.concurrent.ConcurrentHashMap<>();

    private String panOf(Card card) {
        return pans.get(card.getId());
    }

    private UUID approvedAuth(String stan) {
        return jdbc.queryForObject("SELECT id FROM authorization_log WHERE stan = ? AND response_code = '00' AND mti <> '0400' "
                + "ORDER BY created_at DESC LIMIT 1", UUID.class, stan);
    }

    private BigDecimal reversedAmount(UUID auth) {
        return jdbc.queryForObject("SELECT reversed_amount FROM authorization_log WHERE id = ?", BigDecimal.class, auth);
    }

    /** A 0400 with its own STAN naming the original in DE90; {@code amountMinor} null = no DE4. */
    @SuppressWarnings("unchecked")
    private String reverse(String pan, Long amountMinor, String stan, String de90) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("pan", pan, "stan", stan, "originalDataElements", de90));
        if (amountMinor != null) body.put("amount", amountMinor);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + ".FEP_SERVICE");
        var r = rest.exchange("/api/v1/internal/reverse", HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (String) r.getBody().get("responseCode");
    }

    private BigDecimal wallet(Card card) {
        return jdbc.queryForObject("SELECT balance FROM prepaid_wallets WHERE card_id = ?", BigDecimal.class, card.getId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String debitPan() {
        return "4100" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    private SettlementItem itemFor(String stan) {
        UUID authId = jdbc.queryForObject(
                "SELECT id FROM authorization_log WHERE stan = ? AND response_code = '00' ORDER BY created_at DESC LIMIT 1",
                UUID.class, stan);
        return itemRepository.findByAuthorizationLogId(authId).orElseThrow();
    }

    private UUID batchOf(SettlementItem item) {
        return jdbc.queryForObject("SELECT batch_id FROM settlement_items WHERE id = ?", UUID.class, item.getId());
    }

    private String authorize(String pan, String stan, String processingCode, boolean financial, long amountMinor) {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("pan", pan), Map.entry("processingCode", processingCode), Map.entry("amount", amountMinor),
                Map.entry("currencyCode", "840"), Map.entry("stan", stan), Map.entry("terminalId", "TERM0002"),
                Map.entry("merchantId", "M" + stan), Map.entry("merchantName", "Shop " + stan),
                Map.entry("mcc", financial ? "6011" : "5411"), Map.entry("posEntryMode", "051"),
                Map.entry("rrn", "7123" + stan + "00"), Map.entry("scheme", "VISA"), Map.entry("pinVerified", true),
                Map.entry("arqcValid", true), Map.entry("isFinancial", financial));
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

    /** Bodies of the requests the "backend" received whose path ends with {@code suffix}. */
    @SuppressWarnings("unchecked")
    private static List<String> requests(String suffix) {
        Map<String, Object> found = ADMIN.postForObject(backendUrl() + "/__admin/requests/find",
                new HttpEntity<>(Map.of("method", "POST", "urlPattern", ".*" + suffix.replace("/", "\\/")), json()),
                Map.class);
        return ((List<Map<String, Object>>) found.get("requests")).stream()
                .map(r -> String.valueOf(r.get("body"))).toList();
    }

    private static Map<String, Object> hold(boolean approved, String status, String reason) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("approved", approved);
        data.put("holdId", UUID.randomUUID().toString());
        data.put("reference", "ref");
        data.put("status", status);
        data.put("reason", reason);
        data.put("availableBalance", 100.0);
        return Map.of("data", data, "errors", List.of());
    }

    private static void register(String method, String path, Map<String, Object> body) {
        mapping(Map.of("method", method, "urlPath", path), body);
    }

    private static void registerPattern(String method, String pathRegex, Map<String, Object> body) {
        mapping(Map.of("method", method, "urlPathPattern", pathRegex), body);
    }

    private static void mapping(Map<String, Object> request, Map<String, Object> body) {
        ADMIN.postForEntity(backendUrl() + "/__admin/mappings", new HttpEntity<>(Map.of("request", request,
                "response", Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                        "jsonBody", body)), json()), String.class);
    }

    /** A stub that wins over the default ones (WireMock: lower priority number first). */
    private static void priorityMapping(Map<String, Object> request, Map<String, Object> body) {
        ADMIN.postForEntity(backendUrl() + "/__admin/mappings", new HttpEntity<>(Map.of("priority", 1, "request", request,
                "response", Map.of("status", 200, "headers", Map.of("Content-Type", "application/json"),
                        "jsonBody", body)), json()), String.class);
    }

    private static HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
