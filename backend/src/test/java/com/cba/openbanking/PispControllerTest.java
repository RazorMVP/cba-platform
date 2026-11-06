package com.cba.openbanking;

import com.cba.common.exception.CbaException;
import com.cba.openbanking.dto.DomesticPaymentRequest;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.PaymentService;
import com.cba.payment.PaymentStatus;
import com.cba.payment.dto.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PispController — no money moves unless the consent check passes; webhooks once")
class PispControllerTest {

    @Mock ConsentService consentService;
    @Mock PaymentInitiationService paymentInitiationService;
    @Mock PaymentService paymentService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;
    @InjectMocks PispController controller;

    static final String CONSENT = "ob-consent0000000001";
    static final UUID ORG = ConsentServiceTest.ORG;

    private DomesticPaymentRequest request() {
        return new DomesticPaymentRequest(CONSENT, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("25.00"), "USD", "ref", null);
    }

    private PaymentInitiationService.Initiated initiated(boolean replay) {
        PaymentResponse p = mock(PaymentResponse.class);
        when(p.id()).thenReturn(UUID.randomUUID());
        when(p.status()).thenReturn(PaymentStatus.COMPLETED);
        if (!replay) {
            when(p.amount()).thenReturn(new BigDecimal("25.00"));
        }
        UsableConsent consent = new UsableConsent(CONSENT, ORG.toString(), UUID.randomUUID(), Set.of("payments"));
        return new PaymentInitiationService.Initiated(p, consent, replay);
    }

    @Test
    @DisplayName("consent check refuses (foreign account / consent / mismatch): the error stands, no webhook")
    void refused_noWebhook() {
        when(paymentInitiationService.initiate(any()))
                .thenThrow(CbaException.notFound("Account", UUID.randomUUID()));

        assertThatThrownBy(() -> controller.initiateDomesticPayment(null, request()))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
        verifyNoInteractions(webhookDelivery, paymentService);
    }

    @Test
    @DisplayName("new payment: 201, PAYMENT.INITIATED and PAYMENT.COMPLETED sent to the TPP")
    void newPayment_created() {
        var initiated = initiated(false);
        when(paymentInitiationService.initiate(any())).thenReturn(initiated);

        var response = controller.initiateDomesticPayment(null, request());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(webhookDelivery).publishEvent(eq(ORG), eq("PAYMENT.INITIATED"), any());
        verify(webhookDelivery).publishEvent(eq(ORG), eq("PAYMENT.COMPLETED"), any());
    }

    @Test
    @DisplayName("retry of a payment already made: 200 with the same payment, no webhooks again")
    void replay_ok_noWebhook() {
        var initiated = initiated(true);
        when(paymentInitiationService.initiate(any())).thenReturn(initiated);

        var response = controller.initiateDomesticPayment(null, request());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(webhookDelivery);
    }

    @Test
    @DisplayName("GET of a payment the caller didn't initiate: 404, payment never loaded")
    void foreignPayment_notLoaded() {
        UUID paymentId = UUID.randomUUID();
        when(consentService.requireOwnPayment(paymentId)).thenThrow(CbaException.notFound("Payment", paymentId));

        assertThatThrownBy(() -> controller.getDomesticPayment(paymentId, null))
                .hasFieldOrPropertyWithValue("errorCode", "PAYMENT_NOT_FOUND");
        verifyNoInteractions(paymentService);
    }
}
