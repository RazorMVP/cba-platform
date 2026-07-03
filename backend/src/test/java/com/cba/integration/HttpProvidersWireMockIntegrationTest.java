package com.cba.integration;

import com.cba.notification.push.HttpPushSender;
import com.cba.notification.push.PushSender;
import com.cba.notification.sms.HttpSmsProvider;
import com.cba.notification.sms.SmsProvider;
import com.cba.payment.gateway.ExternalPaymentInstruction;
import com.cba.payment.gateway.GatewayResult;
import com.cba.payment.gateway.HttpExternalPaymentGateway;
import com.cba.system.bureau.CreditCheckRequest;
import com.cba.system.bureau.CreditReport;
import com.cba.system.bureau.HttpCreditBureauProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for the four HTTP external-integration providers built in
 * Session 121, driven over a <b>real socket</b> against a WireMock container — not the
 * in-process {@code MockRestServiceServer} the unit tests use.
 *
 * <p>What this proves that the unit tests cannot: the real {@link RestTemplateBuilder}
 * wiring, real Jackson request/response marshalling over the wire, real {@code Bearer}
 * header transmission, connection/read timeouts, and response deserialization — all
 * against a separate process. The WireMock <em>request matchers require</em> the correct
 * {@code Authorization} header and a JSON body field, so a green test means the provider
 * genuinely sent them (a mismatch returns 404 → the provider degrades, and the assertion
 * fails).
 *
 * <p>Each provider is constructed through its <b>production public constructor</b>
 * ({@code RestTemplateBuilder} + {@code Environment}), so the config-reading path is
 * exercised too.
 */
@Testcontainers
@DisplayName("HTTP providers — end-to-end over a WireMock container")
class HttpProvidersWireMockIntegrationTest {

    @Container
    static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    private static final RestTemplate ADMIN = new RestTemplate();

    private static String baseUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }

    @BeforeAll
    static void registerStubs() {
        // Each stub REQUIRES the Bearer header + a JSON body field → proves the provider sent them.
        stub("/sms", "sms-key", 200, Map.of("messageId", "AT-777"), "$.message");
        stub("/bureau", "bureau-key", 200, Map.of("score", 712, "reference", "TU-9"), "$.nationalId");
        stub("/pay", "pay-key", 200, Map.of("uetr", "UETR-123"), "$.beneficiaryBic");
        stub("/push", "push-key", 200, Map.of("messageId", "proj/msg/1"), "$.token");
        // A dead-token endpoint → 404 so HttpPushSender reports invalidToken.
        stub("/push-dead", "push-key", 404, null, "$.token");
    }

    @Test
    @DisplayName("HttpSmsProvider sends over the wire and parses the gateway message id")
    void sms() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.sms.http.url", baseUrl() + "/sms")
                .withProperty("app.sms.http.api-key", "sms-key")
                .withProperty("app.sms.http.sender", "CBA");
        HttpSmsProvider provider = new HttpSmsProvider(new RestTemplateBuilder(), env);

        SmsProvider.SmsResult r = provider.send("+254700000000", "hello over the wire");

        assertThat(r.accepted()).isTrue();
        assertThat(r.providerMessageId()).isEqualTo("AT-777");
    }

    @Test
    @DisplayName("HttpCreditBureauProvider pulls a real HTTP report → HIT with parsed score")
    void creditBureau() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.creditbureau.http.url", baseUrl() + "/bureau")
                .withProperty("app.creditbureau.http.api-key", "bureau-key");
        HttpCreditBureauProvider provider = new HttpCreditBureauProvider(new RestTemplateBuilder(), env);

        CreditReport report = provider.pull(new CreditCheckRequest(UUID.randomUUID(), "NID-1", "Jane", "KE"));

        assertThat(report.status()).isEqualTo(CreditReport.Status.HIT);
        assertThat(report.score()).isEqualTo(712);
        assertThat(report.band()).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("HttpExternalPaymentGateway submits over the wire → accepted with network reference")
    void externalPayment() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.payments.external.http.url", baseUrl() + "/pay")
                .withProperty("app.payments.external.http.api-key", "pay-key");
        HttpExternalPaymentGateway gateway = new HttpExternalPaymentGateway(new RestTemplateBuilder(), env);

        GatewayResult r = gateway.submit(new ExternalPaymentInstruction(
                "SWIFT", new BigDecimal("100.00"), "USD", "Jane Doe",
                "GB33BUKB20201555555555", "BUKBGB22", "Barclays", "GBR", "SHA", "EXT-1"));

        assertThat(r.accepted()).isTrue();
        assertThat(r.networkReference()).isEqualTo("UETR-123");
    }

    @Test
    @DisplayName("HttpPushSender delivers over the wire → accepted with message id")
    void push() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.push.http.url", baseUrl() + "/push")
                .withProperty("app.push.http.api-key", "push-key");
        HttpPushSender sender = new HttpPushSender(new RestTemplateBuilder(), env);

        PushSender.PushResult r = sender.send("device-token-1", "Title", "Body", Map.of("k", "v"));

        assertThat(r.accepted()).isTrue();
        assertThat(r.messageId()).isEqualTo("proj/msg/1");
    }

    @Test
    @DisplayName("HttpPushSender maps a real 404 to an invalid (deactivatable) token")
    void push_deadToken() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("app.push.http.url", baseUrl() + "/push-dead")
                .withProperty("app.push.http.api-key", "push-key");
        HttpPushSender sender = new HttpPushSender(new RestTemplateBuilder(), env);

        PushSender.PushResult r = sender.send("dead-token", "T", "B", null);

        assertThat(r.accepted()).isFalse();
        assertThat(r.tokenInvalid()).isTrue();
    }

    // ── WireMock admin helper ─────────────────────────────────────────────────

    private static void stub(String path, String bearer, int status, Map<String, Object> jsonBody, String requireJsonPath) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("method", "POST");
        request.put("urlPath", path);
        request.put("headers", Map.of("Authorization", Map.of("equalTo", "Bearer " + bearer)));
        if (requireJsonPath != null) {
            request.put("bodyPatterns", List.of(Map.of("matchesJsonPath", requireJsonPath)));
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", status);
        response.put("headers", Map.of("Content-Type", "application/json"));
        if (jsonBody != null) response.put("jsonBody", jsonBody);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ADMIN.postForEntity(baseUrl() + "/__admin/mappings",
                new HttpEntity<>(Map.of("request", request, "response", response), h), String.class);
    }
}
