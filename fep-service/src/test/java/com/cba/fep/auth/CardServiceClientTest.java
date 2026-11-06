package com.cba.fep.auth;

import com.cba.fep.scheme.SchemeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The authorize body is built field by field, so each field card-service reads must be checked here. */
@ExtendWith(MockitoExtension.class)
class CardServiceClientTest {

    @Mock RestTemplate restTemplate;

    @Test
    @DisplayName("authorize sends DE22 and DE19 in the body card-service binds")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void authorizeBodyCarriesEntryModeAndCountry() {
        CardServiceClient client = new CardServiceClient(restTemplate);
        ReflectionTestUtils.setField(client, "cardServiceBaseUrl", "http://card-service");
        when(restTemplate.postForObject(eq("http://card-service/api/v1/internal/authorize"),
                org.mockito.ArgumentMatchers.any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("responseCode", "00", "authorizationCode", "A1"));

        client.authorize(AuthorizationRequest.builder()
                .pan("4111111111111111").stan("000001").scheme(SchemeType.VISA)
                .posEntryMode("071").acquiringCountry("566")
                .billingAmount("000000001150").billingCurrencyCode("840").billingConversionRate("61150000").build());

        ArgumentCaptor<HttpEntity> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq("http://card-service/api/v1/internal/authorize"),
                sent.capture(), eq(Map.class));
        Map<String, Object> body = (Map<String, Object>) sent.getValue().getBody();
        assertThat(body).containsEntry("posEntryMode", "071").containsEntry("acquiringCountry", "566")
                .containsEntry("billingAmount", "000000001150").containsEntry("billingCurrencyCode", "840")
                .containsEntry("billingConversionRate", "61150000");
    }
}
