package com.cba.integration;

import com.cba.account.AccountService;
import com.cba.openbanking.BalanceNotificationDispatcher;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backlog 13 over real HTTP: a TPP authenticated by a partner API key (no JWT — this was a 500)
 * reads a customer's accounts only through its own authorised consent, named in {@code x-consent-id}.
 */
@DisplayName("Open Banking — TPP reads through its own consent, over real HTTP")
class OpenBankingConsentIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accountService;
    @Autowired BalanceNotificationDispatcher balanceDispatcher;

    private UUID customerId;
    private UUID accountId;

    @BeforeEach
    void demoAccount() {
        Map<String, Object> row = jdbc.queryForMap("SELECT id, customer_id FROM accounts ORDER BY account_number LIMIT 1");
        accountId = (UUID) row.get("id");
        customerId = (UUID) row.get("customer_id");
    }

    /** A partner organisation with an active API key; returns the raw key. */
    private String partnerKey(UUID org) {
        jdbc.update("INSERT INTO partner_organizations (id, name, status, tier, environment) VALUES (?, ?, ?, ?, ?)",
                org, "OB-IT " + org, "PRODUCTION", "PRO", "PRODUCTION");
        String raw = "cba_obit_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO partner_api_keys (organization_id, name, key_hash, key_prefix) VALUES (?, ?, ?, ?)",
                org, "ob-it", PartnerApiKeys.hash(raw), raw.substring(0, 12));
        return raw;
    }

    private HttpHeaders as(String key) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.AUTHORIZATION, "ApiKey " + key);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    /** Creates a consent through the API, then marks it authorised (the customer step needs a Keycloak login). */
    private String authorisedConsent(String key, String... scopes) {
        ResponseEntity<Map> created = rest.exchange("/open-banking/v3.1/consents", HttpMethod.POST,
                new HttpEntity<>(Map.of("customerId", customerId, "scopes", scopes), as(key)), Map.class);
        assertThat(created.getStatusCode().value()).as(String.valueOf(created.getBody())).isEqualTo(201);
        String consentId = (String) created.getBody().get("consentId");
        jdbc.update("UPDATE open_banking_consents SET status = 'AUTHORISED' WHERE consent_id = ?", consentId);
        return consentId;
    }

    private ResponseEntity<String> accounts(String key, String consentId) {
        HttpHeaders h = as(key);
        if (consentId != null) h.set("x-consent-id", consentId);
        return rest.exchange("/open-banking/v3.1/aisp/accounts", HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("own authorised consent: 200 with the consent customer's accounts")
    void ownConsent_readsAccounts() {
        String key = partnerKey(UUID.randomUUID());
        String consentId = authorisedConsent(key, "accounts_read");

        ResponseEntity<String> r = accounts(key, consentId);

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
        assertThat(r.getBody()).contains(accountId.toString());
    }

    @Test
    @DisplayName("another TPP's consent: 404; no consent header: 400")
    void otherTppOrNoHeader_refused() {
        String consentId = authorisedConsent(partnerKey(UUID.randomUUID()), "accounts_read");
        String otherKey = partnerKey(UUID.randomUUID());

        ResponseEntity<String> foreign = accounts(otherKey, consentId);
        assertThat(foreign.getStatusCode().value()).as(foreign.getBody()).isEqualTo(404);
        assertThat(foreign.getBody()).contains("CONSENT_NOT_FOUND").doesNotContain(accountId.toString());

        ResponseEntity<String> noHeader = accounts(otherKey, null);
        assertThat(noHeader.getStatusCode().value()).as(noHeader.getBody()).isEqualTo(400);
        assertThat(noHeader.getBody()).contains("CONSENT_ID_REQUIRED");
    }

    @Test
    @DisplayName("a consent the customer hasn't authorised: 403 CONSENT_NOT_AUTHORISED")
    void awaitingConsent_forbidden() {
        String key = partnerKey(UUID.randomUUID());
        ResponseEntity<Map> created = rest.exchange("/open-banking/v3.1/consents", HttpMethod.POST,
                new HttpEntity<>(Map.of("customerId", customerId, "scopes", new String[] {"accounts_read"}), as(key)),
                Map.class);

        ResponseEntity<String> r = accounts(key, (String) created.getBody().get("consentId"));

        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(403);
        assertThat(r.getBody()).contains("CONSENT_NOT_AUTHORISED");
    }

    // ── Backlog 23: staff consents (test profile: no Authorization header = dev bypass, ADMIN) ──

    @Test
    @DisplayName("staff list by customer shows the consent with type and TPP name; staff revoke with a reason → REVOKED")
    void staff_listAndRevoke() {
        UUID org = UUID.randomUUID();
        String consentId = authorisedConsent(partnerKey(org), "accounts_read");

        ResponseEntity<String> list = rest.getForEntity(
                "/api/v1/open-banking/consents?customerId=" + customerId + "&type=AISP&size=100", String.class);
        assertThat(list.getStatusCode().value()).as(list.getBody()).isEqualTo(200);
        assertThat(list.getBody()).contains(consentId).contains("\"consentType\":\"AISP\"").contains("OB-IT " + org);

        ResponseEntity<String> revoked = rest.postForEntity("/api/v1/open-banking/consents/" + consentId + "/revoke",
                Map.of("reason", "customer asked at branch"), String.class);
        assertThat(revoked.getStatusCode().value()).as(revoked.getBody()).isEqualTo(200);
        assertThat(revoked.getBody()).contains("\"status\":\"REVOKED\"");

        ResponseEntity<String> again = rest.postForEntity("/api/v1/open-banking/consents/" + consentId + "/revoke",
                Map.of("reason", "twice"), String.class);
        assertThat(again.getStatusCode().value()).as(again.getBody()).isEqualTo(409);
    }

    // ── Backlog 23b: a suspended TPP is refused; reactivated, it reads again ──

    @Test
    @DisplayName("suspend the TPP: its key no longer reads; reactivate: it reads again (consent untouched)")
    void suspendedTpp_refused() {
        UUID org = UUID.randomUUID();
        String key = partnerKey(org);
        String consentId = authorisedConsent(key, "accounts_read");

        ResponseEntity<String> suspend = rest.postForEntity("/api/v1/partners/" + org + "/suspend",
                Map.of("reason", "fraud report"), String.class);
        assertThat(suspend.getStatusCode().value()).as(suspend.getBody()).isEqualTo(200);

        // In production the key authenticates nothing → 401. The test profile's dev bypass then logs the
        // request in as a placeholder user with no customer record and no organisation, which is refused
        // too (404 CUSTOMER_NOT_FOUND). Either way: not 200, and none of the customer's data.
        ResponseEntity<String> refused = accounts(key, consentId);
        assertThat(refused.getStatusCode().value()).as(refused.getBody()).isIn(401, 403, 404);
        assertThat(refused.getBody()).doesNotContain(accountId.toString());

        ResponseEntity<String> reactivate = rest.postForEntity("/api/v1/partners/" + org + "/reactivate", null, String.class);
        assertThat(reactivate.getStatusCode().value()).as(reactivate.getBody()).isEqualTo(200);
        assertThat(reactivate.getBody()).contains("\"status\":\"PRODUCTION\"");
        assertThat(accounts(key, consentId).getStatusCode().value()).isEqualTo(200);
    }

    // ── Backlog 3: ACCOUNT.BALANCE_UPDATED ──

    @Test
    @DisplayName("a deposit on a consented account marks it pending for the TPP; the dispatcher sends once and clears it")
    void balanceChange_queuedAndSent() throws Exception {
        UUID org = UUID.randomUUID();
        authorisedConsent(partnerKey(org), "balances_read");

        accountService.deposit(accountId, new BigDecimal("3.00"), "BALANCE_UPDATED IT", "it");

        Timestamp pending = null;
        for (int i = 0; i < 50 && pending == null; i++) {   // the notifier runs after commit, off-thread
            pending = jdbc.query("SELECT pending_since FROM partner_balance_notifications WHERE org_id = ? AND account_id = ?",
                    rs -> rs.next() ? rs.getTimestamp(1) : null, org, accountId);
            if (pending == null) Thread.sleep(100);
        }
        assertThat(pending).as("pending row for the TPP after the deposit").isNotNull();

        balanceDispatcher.dispatch(Instant.now());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT pending_since, last_sent_at FROM partner_balance_notifications WHERE org_id = ? AND account_id = ?",
                org, accountId);
        assertThat(row.get("pending_since")).isNull();
        assertThat(row.get("last_sent_at")).isNotNull();
    }
}
