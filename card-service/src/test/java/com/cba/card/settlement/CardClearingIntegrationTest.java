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
    }

    @Autowired TestRestTemplate rest;
    @Autowired CardService cardService;
    @Autowired SettlementService settlementService;
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

    private static HttpHeaders json() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
