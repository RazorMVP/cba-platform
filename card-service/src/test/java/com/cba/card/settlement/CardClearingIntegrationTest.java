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
                hold(false, "CAPTURED", "ALREADY_REFUNDED"));

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
    @DisplayName("15d: a 0200 at a Nigerian acquirer (DE19 566) on a USA card carries the cross-border fee in card-debits; "
            + "the item keeps the split")
    void foreignAtmCarriesCrossBorderFee() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");
        // V3 seed, 40.00 USD: assessment 0.11 % + 0.0195 = 0.0635 → 0.06; cross-border 0.40 % = 0.16 → 0.22.
        // (Domestic it would be 0.06.) The backend echoes what it posted.
        priorityMapping(Map.of("method", "POST", "urlPath", "/api/v1/accounts/" + ACCOUNT + "/card-debits",
                        "bodyPatterns", List.of(Map.of("contains", "\"schemeFeeAmount\":0.22"))),
                postedHold("CAPTURED", 0.65, "PAID", 0.22));

        assertThat(authorize(pan, "950001", "010000", true, 4000, "566")).isEqualTo("00");

        assertThat(requests("/card-debits")).anySatisfy(body -> assertThat(body)
                .contains("\"schemeFeeAmount\":0.22").contains("\"interchangeDirection\":\"PAID\""));
        assertThat(jdbc.queryForObject("SELECT acquiring_country FROM authorization_log WHERE stan = '950001' AND mti = '0200'",
                String.class)).isEqualTo("566");
        SettlementItem item = itemFor("950001");
        assertThat(item.getSchemeFeeAmount()).isEqualByComparingTo("22");
        assertThat(item.getCrossBorderFee()).isEqualByComparingTo("16");
        assertThat(item.getCurrencyFee()).isZero();
        assertThat(item.isCountryUnknown()).isFalse();
        assertThat(item.getGlNet()).isEqualByComparingTo("4087");   // 4000 + 65 paid + 22 fees: in the expected net
    }

    @Test
    @DisplayName("15d: batch close quotes the cross-border fee for a foreign 0100 and stores the split; "
            + "one without DE19 is flagged country unknown")
    void batchCloseStoresSplitAndFlag() {
        String pan = debitPan();
        cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACCOUNT), true, pan, "2912", "123", "USD");
        assertThat(authorize(pan, "960001", "000000", false, 2500, "566")).isEqualTo("00");
        assertThat(authorize(pan, "960002", "000000", false, 2500)).isEqualTo("00");
        SettlementItem foreign = itemFor("960001");
        SettlementItem unknown = itemFor("960002");
        // 25.00 USD: interchange 0.23 earned (MCC 5411); fees 0.0275 + 0.0195 → 0.05, cross-border 0.10 → 0.15.
        register("POST", "/api/v1/accounts/" + ACCOUNT + "/card-holds/" + foreign.getHoldReference() + "/capture",
                postedHold("CAPTURED", 0.23, "EARNED", 0.15));
        register("POST", "/api/v1/accounts/" + ACCOUNT + "/card-holds/" + unknown.getHoldReference() + "/capture",
                postedHold("CAPTURED", 0.23, "EARNED", 0.05));

        settlementService.closeBatch(batchOf(foreign));
        if (!batchOf(unknown).equals(batchOf(foreign))) settlementService.closeBatch(batchOf(unknown));

        assertThat(requests("/card-holds/" + foreign.getHoldReference() + "/capture"))
                .anySatisfy(body -> assertThat(body).contains("\"schemeFeeAmount\":0.15"));
        SettlementItem f = itemRepository.findById(foreign.getId()).orElseThrow();
        assertThat(f.getStatus()).isEqualTo("SETTLED");
        assertThat(f.getCrossBorderFee()).isEqualByComparingTo("10");
        assertThat(f.getCurrencyFee()).isZero();
        assertThat(f.isCountryUnknown()).isFalse();
        assertThat(f.getGlNet()).isEqualByComparingTo("2492");   // 2500 − 23 + 15
        SettlementItem u = itemRepository.findById(unknown.getId()).orElseThrow();
        assertThat(u.isCountryUnknown()).isTrue();
        assertThat(u.getCrossBorderFee()).isZero();
        assertThat(u.getSchemeFeeAmount()).isEqualByComparingTo("5");
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
        return authorize(pan, stan, processingCode, financial, amountMinor, null);
    }

    /** As above, with DE19 (acquiring institution country, ISO 3166 numeric) when not null. */
    private String authorize(String pan, String stan, String processingCode, boolean financial, long amountMinor,
                             String acquiringCountry) {
        Map<String, Object> body = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("pan", pan), Map.entry("processingCode", processingCode), Map.entry("amount", amountMinor),
                Map.entry("currencyCode", "840"), Map.entry("stan", stan), Map.entry("terminalId", "TERM0002"),
                Map.entry("merchantId", "M" + stan), Map.entry("merchantName", "Shop " + stan),
                Map.entry("mcc", financial ? "6011" : "5411"), Map.entry("posEntryMode", "051"),
                Map.entry("rrn", "7123" + stan + "00"), Map.entry("scheme", "VISA"), Map.entry("pinVerified", true),
                Map.entry("arqcValid", true), Map.entry("isFinancial", financial)));
        if (acquiringCountry != null) body.put("acquiringCountry", acquiringCountry);
        return (String) post("/api/v1/internal/authorize", body).get("responseCode");
    }

    /** A backend hold result that echoes the interchange and scheme fee it posted. */
    private static Map<String, Object> postedHold(String status, double interchange, String direction, double schemeFee) {
        Map<String, Object> posted = new java.util.HashMap<>(hold(true, status, null));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = new java.util.HashMap<>((Map<String, Object>) posted.get("data"));
        data.put("interchangeAmount", interchange);
        data.put("interchangeDirection", direction);
        data.put("schemeFeeAmount", schemeFee);
        posted.put("data", data);
        return posted;
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
