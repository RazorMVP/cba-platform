package com.cba.integration;

import com.cba.payment.CallbackSignature;
import com.cba.payment.PaymentService;
import com.cba.payment.PaymentStatus;
import com.cba.payment.dto.ExternalPaymentRequest;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.gateway.ExternalPaymentGateway;
import com.cba.payment.gateway.GatewayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Backlog 1 against real PostgreSQL: an external payment debits the source and posts to 2400 at
 * submit, then the gateway's signed callback settles it (DR 2400 / CR nostro 1002) or returns it
 * (credited back). The gateway is a mock so the submit can be ACCEPTED-not-settled, as a real one
 * answers; the callback goes over HTTP, signed with the test profile's secret.
 */
@DisplayName("External payments — submit, signed callback, GL (backlog 1)")
class ExternalPaymentIT extends AbstractIntegrationTest {

    static final UUID DEMO_CUSTOMER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    static final UUID SAVINGS_PRODUCT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final String SECRET = "it-callback-secret";

    @MockitoBean ExternalPaymentGateway gateway;

    @Autowired PaymentService paymentService;
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void gatewayAccepts() {
        when(gateway.gatewayId()).thenReturn("IT");
        when(gateway.submit(any())).thenAnswer(inv -> GatewayResult.accepted("UETR-" + UUID.randomUUID()));
    }

    @Test
    @DisplayName("submit → PROCESSING, DR 2001 / CR 2400; signed SETTLED → COMPLETED, DR 2400 / CR 1002")
    void submitThenSettle() {
        UUID acc = account("EXT-S", "500.00");
        BigDecimal transitBefore = glCredit("2400");

        PaymentResponse p = pay(acc, "120.00");

        assertThat(p.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(balance(acc)).isEqualByComparingTo("380.00");
        assertThat(lines(p.id(), p.referenceNumber())).containsExactlyInAnyOrder(
                "DEBIT 2001 120.0000 USD", "CREDIT 2400 120.0000 USD");
        assertThat(glCredit("2400").subtract(transitBefore)).isEqualByComparingTo("120.00");

        ResponseEntity<String> r = callback(Map.of("reference", p.referenceNumber(), "status", "SETTLED"));
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getBody()).contains("APPLIED");

        assertThat(status(p.id())).isEqualTo("COMPLETED");
        assertThat(lines(p.id(), p.referenceNumber())).contains("DEBIT 2400 120.0000 USD", "CREDIT 1002 120.0000 USD");
        assertThat(glCredit("2400")).isEqualByComparingTo(transitBefore);

        // The same callback again: a repeat, nothing posted twice.
        ResponseEntity<String> again = callback(Map.of("reference", p.referenceNumber(), "status", "SETTLED"));
        assertThat(again.getBody()).contains("REPEAT");
        assertThat(lines(p.id(), p.referenceNumber())).hasSize(4);
    }

    @Test
    @DisplayName("RETURNED by network reference → source credited back, 2400 back to where it was, RETURNED")
    void returned() {
        UUID acc = account("EXT-R", "500.00");
        BigDecimal transitBefore = glCredit("2400");
        PaymentResponse p = pay(acc, "75.00");

        ResponseEntity<String> r = callback(Map.of("networkReference", p.networkReference(), "status", "RETURNED",
                "reason", "AC04 closed account"));

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(status(p.id())).isEqualTo("RETURNED");
        assertThat(balance(acc)).isEqualByComparingTo("500.00");
        assertThat(glCredit("2400")).isEqualByComparingTo(transitBefore);
        assertThat(lines(p.id(), "RET-" + p.referenceNumber())).containsExactlyInAnyOrder(
                "DEBIT 2400 75.0000 USD", "CREDIT 2001 75.0000 USD");
        // A late SETTLED for a returned payment is refused.
        assertThat(callback(Map.of("reference", p.referenceNumber(), "status", "SETTLED")).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("unsigned, wrongly signed or stale callbacks → 401, nothing moves; nothing from their body is stored")
    void badSignatures() {
        UUID acc = account("EXT-B", "500.00");
        PaymentResponse p = pay(acc, "10.00");
        byte[] body = json(Map.of("reference", p.referenceNumber(), "status", "RETURNED"));
        String now = String.valueOf(Instant.now().getEpochSecond());
        String old = String.valueOf(Instant.now().minusSeconds(900).getEpochSecond());

        assertThat(post(body, null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post(body, now, CallbackSignature.sign("wrong-secret", now, body)).getStatusCode().value()).isEqualTo(401);
        assertThat(post(body, old, CallbackSignature.sign(SECRET, old, body)).getStatusCode().value()).isEqualTo(401);

        assertThat(status(p.id())).isEqualTo("PROCESSING");
        assertThat(balance(acc)).isEqualByComparingTo("490.00");
        // Anyone can send an unsigned body: none of its content is stored (at most a bare "invalid attempt" row).
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_payment_callbacks WHERE result = 'INVALID_SIGNATURE' "
                + "AND (reference IS NOT NULL OR reason IS NOT NULL OR outcome IS NOT NULL)", Integer.class)).isZero();
    }

    @Test
    @DisplayName("two concurrent SETTLED callbacks → one settlement journal (the payment row is locked)")
    void concurrentCallbacks() throws Exception {
        UUID acc = account("EXT-C", "500.00");
        PaymentResponse p = pay(acc, "40.00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                calls.add(() -> callback(Map.of("reference", p.referenceNumber(), "status", "SETTLED")).getStatusCode().value());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND g.gl_code = '1002'""", Integer.class, p.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("ops: POST /{id}/external-outcome RETURNED with a reason (dev-bypass admin ≠ initiator)")
    void manualOutcome() {
        UUID acc = account("EXT-M", "500.00");
        PaymentResponse p = pay(acc, "30.00");

        ResponseEntity<String> r = rest.postForEntity("/api/v1/payments/" + p.id() + "/external-outcome",
                Map.of("outcome", "RETURNED", "reason", "PSP confirmed return by email"), String.class);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(status(p.id())).isEqualTo("RETURNED");
        assertThat(balance(acc)).isEqualByComparingTo("500.00");
        assertThat(jdbc.queryForObject("SELECT outcome_source FROM payments WHERE id = ?", String.class, p.id()))
                .isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("GL ↔ sub-ledger: 2001 moves by exactly the customer balance change across submit and return")
    void reconciliation() {
        UUID acc = account("EXT-REC", "500.00");
        BigDecimal depositsBefore = glCredit("2001");
        PaymentResponse a = pay(acc, "50.00");
        pay(acc, "20.00");
        callback(Map.of("reference", a.referenceNumber(), "status", "RETURNED"));

        assertThat(glCredit("2001").subtract(depositsBefore))
                .isEqualByComparingTo(balance(acc).subtract(new BigDecimal("500.00")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PaymentResponse pay(UUID account, String amount) {
        return paymentService.initiateExternalPayment(new ExternalPaymentRequest(account, new BigDecimal(amount), "USD",
                "SWIFT", "Jane Doe", "GB33BUKB20201555555555", "BUKBGB22", "Barclays", "GBR", "SHA", "it", null), "it-teller");
    }

    private ResponseEntity<String> callback(Map<String, String> fields) {
        byte[] body = json(fields);
        String ts = String.valueOf(Instant.now().getEpochSecond());
        return post(body, ts, CallbackSignature.sign(SECRET, ts, body));
    }

    private ResponseEntity<String> post(byte[] body, String ts, String sig) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (ts != null) h.set("X-Gateway-Timestamp", ts);
        if (sig != null) h.set("X-Gateway-Signature", sig);
        return rest.postForEntity("/api/v1/payments/external/callbacks", new HttpEntity<>(body, h), String.class);
    }

    private static byte[] json(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        fields.forEach((k, v) -> sb.append(sb.length() > 1 ? "," : "").append('"').append(k).append("\":\"").append(v).append('"'));
        return sb.append('}').toString().getBytes(StandardCharsets.UTF_8);
    }

    private UUID account(String number, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO accounts (id, account_number, customer_id, product_id, account_type,
                                      status, balance, currency_code, opened_date, created_by)
                VALUES (?, ?, ?, ?, 'SAVINGS', 'ACTIVE', ?, 'USD', DATE '2025-01-01', 'ext-it')""",
                id, number + "-" + id.toString().substring(0, 6), DEMO_CUSTOMER, SAVINGS_PRODUCT, new BigDecimal(balance));
        return id;
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    private String status(UUID payment) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, payment);
    }

    private List<String> lines(UUID entityId, String reference) {
        return jdbc.queryForList("""
                SELECT je.entry_type || ' ' || g.gl_code || ' ' || je.amount || ' ' || je.currency_code
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE je.entity_id = ? AND je.reference_number = ?""", String.class, entityId, reference);
    }

    /** Credits minus debits on a GL code, USD. */
    private BigDecimal glCredit(String glCode) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN je.entry_type = 'CREDIT' THEN je.amount ELSE -je.amount END), 0)
                FROM journal_entries je JOIN gl_accounts g ON g.id = je.gl_account_id
                WHERE g.gl_code = ? AND je.currency_code = 'USD'""", BigDecimal.class, glCode);
    }
}
