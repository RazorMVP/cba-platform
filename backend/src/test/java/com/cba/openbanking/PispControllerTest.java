package com.cba.openbanking;

import com.cba.common.exception.CbaException;
import com.cba.openbanking.dto.DomesticPaymentRequest;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PispController — no money moves unless the consent check passes")
class PispControllerTest {

    @Mock ConsentService consentService;
    @Mock PaymentService paymentService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;
    @InjectMocks PispController controller;

    static final String CONSENT = "ob-consent0000000001";

    private DomesticPaymentRequest request(UUID source) {
        return new DomesticPaymentRequest(CONSENT, source, UUID.randomUUID(),
                new BigDecimal("25.00"), "USD", "ref", null);
    }

    @Test
    @DisplayName("source account outside the consent's customer: 404 and no transfer")
    void foreignSourceAccount_noTransfer() {
        UUID source = UUID.randomUUID();
        when(consentService.authorisePaymentInitiation(CONSENT, source))
                .thenThrow(CbaException.notFound("Account", source));

        assertThatThrownBy(() -> controller.initiateDomesticPayment(null, request(source)))
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_NOT_FOUND");
        verifyNoInteractions(paymentService, webhookDelivery);
    }

    @Test
    @DisplayName("another TPP's consent: 404 and no transfer")
    void foreignConsent_noTransfer() {
        UUID source = UUID.randomUUID();
        when(consentService.authorisePaymentInitiation(CONSENT, source))
                .thenThrow(CbaException.notFound("Consent", CONSENT));

        assertThatThrownBy(() -> controller.initiateDomesticPayment(null, request(source)))
                .hasFieldOrPropertyWithValue("errorCode", "CONSENT_NOT_FOUND");
        verify(paymentService, never()).transfer(any(), any());
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
