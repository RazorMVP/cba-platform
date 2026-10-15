package com.cba.fep.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("FepConfig — card-service RestTemplate carries the service token")
class FepConfigTest {

    HttpServer keycloak;

    @AfterEach
    void stop() {
        if (keycloak != null) keycloak.stop(0);
    }

    @Test
    @DisplayName("with a client secret: every card-service call carries Bearer <token>; the token is fetched once")
    void secret_bearerOnCardServiceCalls() throws Exception {
        AtomicInteger tokenRequests = new AtomicInteger();
        keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext("/token", exchange -> {
            tokenRequests.incrementAndGet();
            byte[] body = "{\"access_token\":\"fep-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        keycloak.start();
        String tokenUri = "http://127.0.0.1:" + keycloak.getAddress().getPort() + "/token";

        RestTemplate rest = FepConfig.cardServiceRestTemplate(2000, 5000, "cba-fep-service", "s3cret",
                tokenUri, Clock.systemUTC());
        MockRestServiceServer cardService = MockRestServiceServer.bindTo(rest).build();
        cardService.expect(requestTo("http://card-service/api/v1/internal/detokenize"))
                .andExpect(header("Authorization", "Bearer fep-token"))
                .andRespond(withSuccess());
        cardService.expect(requestTo("http://card-service/api/v1/bins/all"))
                .andExpect(header("Authorization", "Bearer fep-token"))
                .andRespond(withSuccess());

        rest.postForObject("http://card-service/api/v1/internal/detokenize", java.util.Map.of("dpan", "x"), String.class);
        rest.getForObject("http://card-service/api/v1/bins/all", String.class);

        cardService.verify();
        assertThat(tokenRequests).hasValue(1);
    }

    @Test
    @DisplayName("without a client secret: no interceptor, calls go unauthenticated (dev with card-service bypass)")
    void noSecret_noInterceptor() {
        assertThat(FepConfig.cardServiceRestTemplate(2000, 5000, "cba-fep-service", "", "http://x/token",
                Clock.systemUTC()).getInterceptors()).isEmpty();
    }
}
