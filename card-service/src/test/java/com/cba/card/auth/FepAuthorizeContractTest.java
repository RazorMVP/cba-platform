package com.cba.card.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the FEP↔card-service <b>authorize</b> wire contract: the exact JSON body that
 * fep-service's {@code CardServiceClient.authorize(...)} sends must deserialize cleanly into
 * {@link CardAuthRequest}, and {@link CardAuthResponse} must expose the fields the FEP reads.
 *
 * <p>This is the test that was missing — both services mock each other in their own suites, so
 * nothing exercised the real contract. That gap hid a base-path mismatch (fixed Session 121
 * cont. 7–8) and the {@code schemeData}/{@code emvTags} field-name mismatch (fixed here).
 */
class FepAuthorizeContractTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Mirrors, key-for-key, the body map built by fep-service {@code CardServiceClient.authorize}. */
    private Map<String, Object> fepAuthorizeBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("pan", "4111111111111111");
        body.put("processingCode", "000000");
        body.put("amount", 5000);
        body.put("currencyCode", "840");
        body.put("stan", "123456");
        body.put("rrn", "123456789012");
        body.put("terminalId", "TERM0001");
        body.put("merchantId", "MERCH000000001");
        body.put("merchantName", "ACME STORE");
        body.put("mcc", "5411");
        body.put("posEntryMode", "CHIP");
        body.put("scheme", "VISA");
        body.put("pinVerified", true);
        body.put("arqcValid", true);
        body.put("isFinancial", false);
        body.put("schemeData", Map.of("9F26", "AABBCCDD"));
        return body;
    }

    @Test
    @DisplayName("the FEP authorize body deserializes into CardAuthRequest with every used field bound")
    void fepBodyBindsToCardAuthRequest() throws Exception {
        String json = om.writeValueAsString(fepAuthorizeBody());

        CardAuthRequest req = om.readValue(json, CardAuthRequest.class);

        assertThat(req.pan()).isEqualTo("4111111111111111");
        assertThat(req.processingCode()).isEqualTo("000000");
        assertThat(req.amount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(req.currencyCode()).isEqualTo("840");
        assertThat(req.stan()).isEqualTo("123456");
        assertThat(req.rrn()).isEqualTo("123456789012");
        assertThat(req.terminalId()).isEqualTo("TERM0001");
        assertThat(req.merchantId()).isEqualTo("MERCH000000001");
        assertThat(req.mcc()).isEqualTo("5411");
        assertThat(req.posEntryMode()).isEqualTo("CHIP");
        assertThat(req.scheme()).isEqualTo("VISA");
        assertThat(req.pinVerified()).isTrue();
        assertThat(req.arqcValid()).isTrue();
        assertThat(req.isFinancial()).isFalse();
        // The field the name-mismatch dropped — now bound because both sides agree on "schemeData".
        assertThat(req.schemeData()).containsEntry("9F26", "AABBCCDD");
        // Not sent by the FEP → null, and that's fine (unused by card-service logic).
        assertThat(req.posConditionCode()).isNull();
    }

    @Test
    @DisplayName("CardAuthResponse exposes the fields the FEP parseAuthResult reads")
    void responseExposesFieldsFepReads() throws Exception {
        String json = om.writeValueAsString(CardAuthResponse.approve("AUTH01", null, "840"));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = om.readValue(json, Map.class);
        assertThat(map).containsKeys("responseCode", "authorizationCode", "standIn");
        assertThat(map.get("responseCode")).isEqualTo("00");
        assertThat(map.get("authorizationCode")).isEqualTo("AUTH01");
    }
}
