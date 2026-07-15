package com.cba.openbanking;

import com.cba.common.exception.CbaException;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.PaymentReversedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReversalPartnerNotifier — PAYMENT.REVERSED fan-out")
class PaymentReversalPartnerNotifierTest {

    @Mock ConsentService consentService;
    @Mock PartnerWebhookDeliveryService webhookDelivery;

    @InjectMocks PaymentReversalPartnerNotifier notifier;

    private PaymentReversedEvent event(String createdBy) {
        return new PaymentReversedEvent(UUID.randomUUID(), createdBy,
                new BigDecimal("100.00"), "REV-PAY-1", "customer request");
    }

    @Test
    @DisplayName("partner-initiated (PISP) payment → resolves org and fires PAYMENT.REVERSED")
    void pispPayment_publishes() {
        UUID org = UUID.randomUUID();
        when(consentService.tppClientIdFor("ob-123")).thenReturn(org.toString());

        notifier.onPaymentReversed(event("open-banking:ob-123"));

        verify(webhookDelivery).publishEvent(eq(org), eq("PAYMENT.REVERSED"), any());
    }

    @Test
    @DisplayName("non-PISP actor (teller) → no consent lookup, no webhook")
    void nonPisp_ignored() {
        notifier.onPaymentReversed(event("teller1"));

        verifyNoInteractions(consentService, webhookDelivery);
    }

    @Test
    @DisplayName("null actor → ignored")
    void nullActor_ignored() {
        notifier.onPaymentReversed(event(null));

        verifyNoInteractions(consentService, webhookDelivery);
    }

    @Test
    @DisplayName("external TPP (non-UUID tppClientId) → org unresolved, no webhook")
    void externalTpp_noPublish() {
        when(consentService.tppClientIdFor("ob-x")).thenReturn("external-tpp-not-a-uuid");

        notifier.onPaymentReversed(event("open-banking:ob-x"));

        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }

    @Test
    @DisplayName("consent lookup throws (deleted/malformed) → swallowed, no webhook")
    void consentLookupThrows_swallowed() {
        when(consentService.tppClientIdFor("ob-gone"))
                .thenThrow(CbaException.notFound("Consent", "ob-gone"));

        notifier.onPaymentReversed(event("open-banking:ob-gone"));

        verify(webhookDelivery, never()).publishEvent(any(), any(), any());
    }
}
