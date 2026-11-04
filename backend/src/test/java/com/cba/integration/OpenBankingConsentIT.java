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
}
