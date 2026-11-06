package com.cba.integration;

import com.cba.partner.PartnerApiKeys;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backlog 24 over real HTTP and PostgreSQL: a payment consent names one payment (amount, currency,
 * creditor); a payment must match it and uses the consent up. The first PISP integration test.
 */
@DisplayName("Open Banking PISP — one payment per consent, exactly as authorised, over real HTTP")
class OpenBankingPispIT extends AbstractIntegrationTest {

    // Demo data (V2): John's and Jane's savings accounts
    static final UUID JOHN_SAVINGS = UUID.fromString("40000000-0000-0000-0000-000000000001");
    static final UUID JANE_SAVINGS = UUID.fromString("40000000-0000-0000-0000-000000000003");

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;

    private UUID customerId;
    private String currency;

    @BeforeEach
    void source() {
        Map<String, Object> row = jdbc.queryForMap("SELECT customer_id, currency_code FROM accounts WHERE id = ?", JOHN_SAVINGS);
        customerId = (UUID) row.get("customer_id");
        currency = (String) row.get("currency_code");
    }

    private String partnerKey() {
        UUID org = UUID.randomUUID();
        jdbc.update("INSERT INTO partner_organizations (id, name, status, tier, environment) VALUES (?, ?, ?, ?, ?)",
                org, "PISP-IT " + org, "PRODUCTION", "PRO", "PRODUCTION");
        String raw = "cba_pispit_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO partner_api_keys (organization_id, name, key_hash, key_prefix) VALUES (?, ?, ?, ?)",
                org, "pisp-it", PartnerApiKeys.hash(raw), raw.substring(0, 12));
        return raw;
    }

    private HttpHeaders as(String key) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + key);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** A payment consent for {@code amount}, created through the API, then marked authorised. */
    private String authorisedPaymentConsent(String key, String amount) {
        Map<String, Object> initiation = new LinkedHashMap<>();
        initiation.put("instructedAmount", amount);
        initiation.put("currency", currency);
        initiation.put("creditorAccountId", JANE_SAVINGS);
        initiation.put("reference", "PISP-IT");
        ResponseEntity<Map> created = rest.exchange("/open-banking/v3.1/consents", HttpMethod.POST,
                new HttpEntity<>(Map.of("customerId", customerId, "scopes", List.of("payments"),
                        "initiation", initiation), as(key)), Map.class);
        assertThat(created.getStatusCode().value()).as(String.valueOf(created.getBody())).isEqualTo(201);
        String consentId = (String) created.getBody().get("consentId");
        jdbc.update("UPDATE open_banking_consents SET status = 'AUTHORISED' WHERE consent_id = ?", consentId);
        return consentId;
    }

    private ResponseEntity<Map> pay(String key, String consentId, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("consentId", consentId);
        body.put("sourceAccountId", JOHN_SAVINGS);
        body.put("destinationAccountId", JANE_SAVINGS);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("reference", "PISP-IT");
        return rest.exchange("/open-banking/v3.1/pisp/domestic-payments", HttpMethod.POST,
                new HttpEntity<>(body, as(key)), Map.class);
    }

    private int paymentsUnder(String consentId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE created_by = ?", Integer.class,
                "open-banking:" + consentId);
    }

    private BigDecimal balance(UUID account) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
    }

    @Test
    @DisplayName("matching payment: 201, money moves once, consent CONSUMED; the same payment again: 200, same id, no second transfer")
    void pay_thenReplay() {
        String key = partnerKey();
        String consentId = authorisedPaymentConsent(key, "1.25");
        BigDecimal before = balance(JOHN_SAVINGS);

        ResponseEntity<Map> first = pay(key, consentId, "1.25");
        assertThat(first.getStatusCode().value()).as(String.valueOf(first.getBody())).isEqualTo(201);

        ResponseEntity<Map> again = pay(key, consentId, "1.25");
        assertThat(again.getStatusCode().value()).as(String.valueOf(again.getBody())).isEqualTo(200);
        assertThat(again.getBody().get("domesticPaymentId")).isEqualTo(first.getBody().get("domesticPaymentId"));

        assertThat(paymentsUnder(consentId)).isEqualTo(1);
        assertThat(balance(JOHN_SAVINGS)).isEqualByComparingTo(before.subtract(new BigDecimal("1.25")));
        assertThat(jdbc.queryForObject("SELECT status FROM open_banking_consents WHERE consent_id = ?",
                String.class, consentId)).isEqualTo("CONSUMED");
    }

    @Test
    @DisplayName("a different amount than authorised: 400 PAYMENT_DOES_NOT_MATCH_CONSENT, nothing moves, consent still usable")
    void differentAmount_refused() {
        String key = partnerKey();
        String consentId = authorisedPaymentConsent(key, "2.00");
        BigDecimal before = balance(JOHN_SAVINGS);

        ResponseEntity<Map> r = pay(key, consentId, "2000.00");

        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(400);
        assertThat(String.valueOf(r.getBody())).contains("PAYMENT_DOES_NOT_MATCH_CONSENT");
        assertThat(paymentsUnder(consentId)).isZero();
        assertThat(balance(JOHN_SAVINGS)).isEqualByComparingTo(before);
        assertThat(pay(key, consentId, "2.00").getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("two payments at the same moment under one consent: exactly one transfer (the consent row lock)")
    void concurrent_oneTransfer() throws Exception {
        String key = partnerKey();
        String consentId = authorisedPaymentConsent(key, "0.75");
        BigDecimal before = balance(JOHN_SAVINGS);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<ResponseEntity<Map>>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> pay(key, consentId, "0.75"));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<ResponseEntity<Map>> f : pool.invokeAll(calls)) {
                statuses.add(f.get().getStatusCode().value());
            }
            assertThat(statuses).containsOnly(201, 200);
            assertThat(statuses.stream().filter(s -> s == 201)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(paymentsUnder(consentId)).isEqualTo(1);
        assertThat(balance(JOHN_SAVINGS)).isEqualByComparingTo(before.subtract(new BigDecimal("0.75")));
    }

    @Test
    @DisplayName("a payments consent without an initiation: 400 PAYMENT_INITIATION_REQUIRED at creation")
    void noInitiation_refused() {
        ResponseEntity<String> r = rest.exchange("/open-banking/v3.1/consents", HttpMethod.POST,
                new HttpEntity<>(Map.of("customerId", customerId, "scopes", List.of("payments")), as(partnerKey())),
                String.class);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(400);
        assertThat(r.getBody()).contains("PAYMENT_INITIATION_REQUIRED");
    }
}
