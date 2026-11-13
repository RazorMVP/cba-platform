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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settlement reconciliation (backlog 15d, D3a) over real HTTP and PostgreSQL, with a WireMock container as
 * the backend: the three lists return exactly the rows they should, and Retry release stamps an unresolved
 * hold only when the backend answers with a resolution.
 */
@DisplayName("Settlement reconciliation — unresolved holds, stuck clearing, unknown country; retry release")
class ReconciliationIntegrationTest extends AbstractCardIntegrationTest {

    static final UUID DEBIT_PRODUCT = UUID.fromString("11111111-0001-0001-0001-000000000001");
    /** Each account answers the release differently. */
    static final String ACC_RELEASED = "cccccccc-0000-0000-0000-00000000d001";
    static final String ACC_NOT_FOUND = "cccccccc-0000-0000-0000-00000000d002";
    static final String ACC_CLOSED = "cccccccc-0000-0000-0000-00000000d003";
    static final String ACC_DOWN = "cccccccc-0000-0000-0000-00000000d004";
    static final String ACC_SLOW = "cccccccc-0000-0000-0000-00000000d005";
    static final String ACC_FLAKY = "cccccccc-0000-0000-0000-00000000d006";

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
        registry.add("backend.base-url", ReconciliationIntegrationTest::backendUrl);
    }

    @BeforeAll
    static void stubs() {
        release(ACC_RELEASED, hold(true, "RELEASED", null), 0, 200);
        release(ACC_NOT_FOUND, hold(false, null, "HOLD_NOT_FOUND"), 0, 200);
        release(ACC_CLOSED, hold(false, "CAPTURED", "ACCOUNT_CLOSED"), 0, 200);
        release(ACC_DOWN, Map.of(), 0, 503);
        release(ACC_SLOW, hold(true, "RELEASED", null), 700, 200);
        // ACC_FLAKY: the hold call and its release both fail → the decline keeps the reference.
        stub(Map.of("method", "POST", "urlPath", "/api/v1/accounts/" + ACC_FLAKY + "/card-holds"), Map.of(), 0, 503, 5);
        release(ACC_FLAKY, Map.of(), 0, 503);
    }

    @Autowired TestRestTemplate rest;
    @Autowired CardService cardService;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("the three lists return exactly their rows: not resolved / approved / no-reference / current-day / known-country ones")
    void listsReturnExactlyTheRightRows() {
        Card card = debitCard(ACC_RELEASED);
        // A day of its own (the database may be shared), far in the past.
        LocalDate day = LocalDate.of(1990, 1, 10).plusDays(ThreadLocalRandom.current().nextInt(8000));
        String noon = day + " 12:00:00+00";
        // Holds: only an unresolved RC 91 with a reference, inside the range.
        UUID unresolved = authLog(card, "91", "CARD-RECON-A1", noon, false);
        authLog(card, "91", "CARD-RECON-A2", noon, true);          // resolved
        authLog(card, "00", "CARD-RECON-A3", noon, false);         // approved
        authLog(card, "91", null, noon, false);                    // no reference
        UUID later = authLog(card, "91", "CARD-RECON-A5", day.plusDays(6) + " 12:00:00+00", false);   // out of range

        // Items: a CLEARING item of an earlier date is stuck; one of today isn't (close is still on it).
        UUID oldBatch = batch(day, "CLOSED");
        UUID todayBatch = batch(LocalDate.now(), "CLOSED");
        UUID stuck = item(oldBatch, card, "CLEARING", false);
        UUID pending = item(oldBatch, card, "PENDING", false);
        UUID clearingToday = item(todayBatch, card, "CLEARING", false);
        UUID unknownCountry = item(oldBatch, card, "SETTLED", true);
        item(oldBatch, card, "SETTLED", false);

        Map<String, Object> view = data(get("?from=" + day.minusDays(1) + "&to=" + day.plusDays(1), "ADMIN"));

        assertThat(ids(view, "unresolvedHolds", "authorizationId")).containsExactly(unresolved.toString());
        Map<String, Object> hold = rows(view, "unresolvedHolds").get(0);
        assertThat(hold).containsEntry("holdReference", "CARD-RECON-A1").containsEntry("stan", "910001")
                .containsEntry("currency", "USD").containsEntry("panLast4", card.getPanSuffix())
                .doesNotContainKeys("pan", "panPrefix", "panEncrypted", "customerId");   // never the PAN
        assertThat(((Number) hold.get("amount")).doubleValue()).isEqualTo(25.00);

        assertThat(ids(view, "stuckClearing", "itemId")).containsExactly(stuck.toString());
        assertThat(rows(view, "stuckClearing").get(0)).containsEntry("batchStatus", "CLOSED")
                .containsEntry("settlementDate", day.toString()).containsEntry("holdReference", "CARD-ITEM");
        assertThat(((Number) rows(view, "stuckClearing").get(0).get("amount")).doubleValue()).isEqualTo(12.50);
        assertThat(ids(view, "countryUnknown", "itemId")).containsExactly(unknownCountry.toString());

        // Without a range: the later hold appears, today's CLEARING item and the PENDING one still don't.
        Map<String, Object> all = data(get("", "ADMIN"));
        assertThat(ids(all, "unresolvedHolds", "authorizationId")).contains(unresolved.toString(), later.toString());
        assertThat(ids(all, "stuckClearing", "itemId")).contains(stuck.toString())
                .doesNotContain(clearingToday.toString(), pending.toString());
    }

    @Test
    @DisplayName("an RC 91 decline whose release can't reach the backend is listed; retry → 503, nothing stamped; backend back → RELEASED; again → 409")
    void endToEndDeclineThenRetry() {
        String pan = debitPan();
        Card card = cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(ACC_FLAKY), true, pan,
                "2912", "123", "USD");
        String stan = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        assertThat(authorize(pan, stan, 2500)).isEqualTo("91");
        UUID authId = jdbc.queryForObject("SELECT id FROM authorization_log WHERE card_id = ? AND stan = ?",
                UUID.class, card.getId(), stan);

        assertThat(ids(data(get("", "ADMIN")), "unresolvedHolds", "authorizationId")).contains(authId.toString());

        ResponseEntity<Map> down = retry(authId, "ADMIN");
        assertThat(down.getStatusCode().value()).isEqualTo(503);
        assertThat(errorCode(down)).isEqualTo("BACKEND_UNAVAILABLE");
        assertThat(resolvedAt(authId)).isNull();

        // The backend is back.
        stub(Map.of("method", "POST", "urlPathPattern", "/api/v1/accounts/" + ACC_FLAKY + "/card-holds/[^/]+/release"),
                hold(true, "RELEASED", null), 0, 200, 1);
        ResponseEntity<Map> ok = retry(authId, "ADMIN");
        assertThat(ok.getStatusCode().value()).as(String.valueOf(ok.getBody())).isEqualTo(200);
        assertThat(data(ok)).containsEntry("resolution", "RELEASED").containsEntry("backendStatus", "RELEASED");
        assertThat(resolvedAt(authId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT hold_resolution FROM authorization_log WHERE id = ?", String.class, authId))
                .isEqualTo("RELEASED");
        String reference = jdbc.queryForObject("SELECT hold_reference FROM authorization_log WHERE id = ?",
                String.class, authId);
        // The decline's own release, the 503 retry and the one that worked: the same amount and currency each time.
        assertThat(requests("/card-holds/" + reference + "/release")).hasSize(3).allSatisfy(body ->
                assertThat(body).contains("\"amount\":25.00").contains("\"currencyCode\":\"USD\""));
        assertThat(ids(data(get("", "ADMIN")), "unresolvedHolds", "authorizationId")).doesNotContain(authId.toString());

        ResponseEntity<Map> again = retry(authId, "ADMIN");
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("HOLD_ALREADY_RESOLVED");
    }

    @Test
    @DisplayName("HOLD_NOT_FOUND → stamped NOT_FOUND_TOMBSTONED (the backend recorded the reference as released)")
    void notFoundIsTombstoned() {
        UUID authId = authLog(debitCard(ACC_NOT_FOUND), "91", "CARD-RECON-NF", "2001-04-01 12:00:00+00", false);
        ResponseEntity<Map> r = retry(authId, "ADMIN");
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        assertThat(data(r)).containsEntry("resolution", "NOT_FOUND_TOMBSTONED")
                .containsEntry("backendReason", "HOLD_NOT_FOUND").containsEntry("holdReference", "CARD-RECON-NF");
        assertThat(jdbc.queryForObject("SELECT hold_resolution FROM authorization_log WHERE id = ?", String.class, authId))
                .isEqualTo("NOT_FOUND_TOMBSTONED");
        assertThat(jdbc.queryForObject("SELECT hold_resolved_by FROM authorization_log WHERE id = ?", String.class, authId))
                .isNotBlank();
    }

    @Test
    @DisplayName("a backend refusal → 409 with its reason, nothing stamped; unreachable → 503, nothing stamped")
    void refusalAndUnreachableStampNothing() {
        UUID refused = authLog(debitCard(ACC_CLOSED), "91", "CARD-RECON-RF", "2001-04-02 12:00:00+00", false);
        ResponseEntity<Map> r = retry(refused, "ADMIN");
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(r)).isEqualTo("ACCOUNT_CLOSED");
        assertThat(resolvedAt(refused)).isNull();

        UUID down = authLog(debitCard(ACC_DOWN), "91", "CARD-RECON-DN", "2001-04-02 12:00:00+00", false);
        ResponseEntity<Map> d = retry(down, "ADMIN");
        assertThat(d.getStatusCode().value()).isEqualTo(503);
        assertThat(errorCode(d)).isEqualTo("BACKEND_UNAVAILABLE");
        assertThat(resolvedAt(down)).isNull();
    }

    @Test
    @DisplayName("not an unresolved hold → 404; unknown id → 404")
    void notUnresolvedIs404() {
        UUID approved = authLog(debitCard(ACC_RELEASED), "00", "CARD-RECON-OK", "2001-04-03 12:00:00+00", false);
        assertThat(retry(approved, "ADMIN").getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(retry(approved, "ADMIN"))).isEqualTo("UNRESOLVED_HOLD_NOT_FOUND");
        assertThat(retry(UUID.randomUUID(), "ADMIN").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("two admins retrying at once: one stamps, the other gets 409 HOLD_ALREADY_RESOLVED")
    void concurrentRetriesStampOnce() {
        UUID authId = authLog(debitCard(ACC_SLOW), "91", "CARD-RECON-SL", "2001-04-04 12:00:00+00", false);
        CompletableFuture<ResponseEntity<Map>> a = CompletableFuture.supplyAsync(() -> retry(authId, "ADMIN"));
        CompletableFuture<ResponseEntity<Map>> b = CompletableFuture.supplyAsync(() -> retry(authId, "ADMIN"));
        List<Integer> codes = new ArrayList<>(List.of(a.join().getStatusCode().value(), b.join().getStatusCode().value()));
        codes.sort(Integer::compareTo);
        assertThat(codes).containsExactly(200, 409);
        assertThat(resolvedAt(authId)).isNotNull();
    }

    @Test
    @DisplayName("TELLER (not ADMIN) → 403 on the view and on retry")
    void tellerIsForbidden() {
        assertThat(get("", "TELLER").getStatusCode().value()).isEqualTo(403);
        UUID authId = authLog(debitCard(ACC_RELEASED), "91", "CARD-RECON-TL", "2001-04-05 12:00:00+00", false);
        assertThat(retry(authId, "TELLER").getStatusCode().value()).isEqualTo(403);
        assertThat(resolvedAt(authId)).isNull();
    }

    @Test
    @DisplayName("from after to → 400 INVALID_DATE_RANGE")
    void invalidRange() {
        ResponseEntity<Map> r = get("?from=2001-03-05&to=2001-03-03", "ADMIN");
        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(r)).isEqualTo("INVALID_DATE_RANGE");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Card debitCard(String account) {
        return cardService.issueCard(DEBIT_PRODUCT, UUID.randomUUID(), UUID.fromString(account), true, debitPan(),
                "2912", "123", "USD");
    }

    private static String debitPan() {
        return "4100" + String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    /** An authorization_log row: 25.00 USD (2500 minor, DE49 840), STAN 910001. */
    private UUID authLog(Card card, String responseCode, String holdReference, String createdAt, boolean resolved) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO authorization_log (id, card_id, stan, rrn, mti, processing_code, amount, currency_code,
                    response_code, is_financial, scheme, hold_reference, created_at,
                    hold_resolved_at, hold_resolved_by, hold_resolution)
                VALUES (?, ?, '910001', '712391000100', '0100', '000000', 2500, '840', ?, false, 'VISA', ?,
                    CAST(? AS TIMESTAMPTZ), ?, ?, ?)
                """, id, card.getId(), responseCode, holdReference, createdAt,
                resolved ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                resolved ? "ops" : null, resolved ? "RELEASED" : null);
        return id;
    }

    private UUID batch(LocalDate date, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO settlement_batches (id, batch_ref, status, settlement_date) VALUES (?, ?, ?, ?)",
                id, "RECON-" + id.toString().substring(0, 8), status, date);
        return id;
    }

    private UUID item(UUID batchId, Card card, String status, boolean countryUnknown) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO settlement_items (id, batch_id, amount, currency_code, status, card_id, account_id,
                    card_type, hold_reference, scheme, country_unknown)
                VALUES (?, ?, 1250, '840', ?, ?, ?, 'DEBIT', 'CARD-ITEM', 'VISA', ?)
                """, id, batchId, status, card.getId(), card.getLinkedEntityId(), countryUnknown);
        return id;
    }

    private Object resolvedAt(UUID authId) {
        return jdbc.queryForObject("SELECT hold_resolved_at FROM authorization_log WHERE id = ?", Object.class, authId);
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> get(String query, String role) {
        return rest.exchange("/api/v1/cards/settlement/reconciliation" + query, HttpMethod.GET,
                new HttpEntity<>(auth(role)), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> retry(UUID authId, String role) {
        return rest.exchange("/api/v1/cards/settlement/reconciliation/holds/" + authId + "/release", HttpMethod.POST,
                new HttpEntity<>(auth(role)), Map.class);
    }

    private static HttpHeaders auth(String role) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(ROLES_TOKEN + "." + role);
        return h;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> data(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (Map<String, Object>) r.getBody().get("data");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String errorCode(ResponseEntity<Map> r) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) r.getBody().get("errors");
        return String.valueOf(errors.get(0).get("code"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> view, String list) {
        return (List<Map<String, Object>>) view.get(list);
    }

    private static List<String> ids(Map<String, Object> view, String list, String key) {
        return rows(view, list).stream().map(r -> String.valueOf(r.get(key))).toList();
    }

    private String authorize(String pan, String stan, long amountMinor) {
        Map<String, Object> body = new HashMap<>(Map.ofEntries(
                Map.entry("pan", pan), Map.entry("processingCode", "000000"), Map.entry("amount", amountMinor),
                Map.entry("currencyCode", "840"), Map.entry("stan", stan), Map.entry("terminalId", "TERM0003"),
                Map.entry("merchantId", "M" + stan), Map.entry("merchantName", "Shop " + stan),
                Map.entry("mcc", "5411"), Map.entry("posEntryMode", "051"),
                Map.entry("rrn", "7124" + stan + "00"), Map.entry("scheme", "VISA"), Map.entry("pinVerified", true),
                Map.entry("arqcValid", true), Map.entry("isFinancial", false)));
        HttpHeaders h = auth("FEP_SERVICE");
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> r = rest.exchange("/api/v1/internal/authorize", HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (String) r.getBody().get("responseCode");
    }

    // ── WireMock ─────────────────────────────────────────────────────────────

    private static Map<String, Object> hold(boolean approved, String status, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("approved", approved);
        data.put("holdId", UUID.randomUUID().toString());
        data.put("reference", "ref");
        data.put("status", status);
        data.put("reason", reason);
        data.put("availableBalance", 100.0);
        return Map.of("data", data, "errors", List.of());
    }

    private static void release(String account, Map<String, Object> body, int delayMs, int status) {
        stub(Map.of("method", "POST", "urlPathPattern", "/api/v1/accounts/" + account + "/card-holds/[^/]+/release"),
                body, delayMs, status, 5);
    }

    private static void stub(Map<String, Object> request, Map<String, Object> body, int delayMs, int status, int priority) {
        Map<String, Object> response = new HashMap<>(Map.of("status", status,
                "headers", Map.of("Content-Type", "application/json"), "jsonBody", body));
        if (delayMs > 0) response.put("fixedDelayMilliseconds", delayMs);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ADMIN.postForEntity(backendUrl() + "/__admin/mappings",
                new HttpEntity<>(Map.of("priority", priority, "request", request, "response", response), h), String.class);
    }

    @SuppressWarnings("unchecked")
    private static List<String> requests(String suffixRegex) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> found = ADMIN.postForObject(backendUrl() + "/__admin/requests/find",
                new HttpEntity<>(Map.of("method", "POST", "urlPattern", ".*" + suffixRegex), h), Map.class);
        return ((List<Map<String, Object>>) found.get("requests")).stream()
                .map(r -> String.valueOf(r.get("body"))).toList();
    }
}
