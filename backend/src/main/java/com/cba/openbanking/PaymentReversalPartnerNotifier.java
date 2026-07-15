package com.cba.openbanking;

import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.PaymentReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Fires the {@code PAYMENT.REVERSED} partner webhook when a payment that a partner initiated
 * (via PISP) is reversed.
 *
 * <p>Lives in {@code openbanking} — not {@code payment} — so the payment package stays free of
 * any partner/consent dependency. {@code PaymentService} publishes a plain
 * {@link PaymentReversedEvent}; this listener attributes it. PISP payments are created with the
 * actor {@code "open-banking:{consentId}"} (see {@link PispController}), so the original payment's
 * {@code createdBy} is the consent pointer — no extra column or migration needed.
 *
 * <p>{@code AFTER_COMMIT} ensures the partner is only told about a reversal that actually
 * committed; {@code @Async} keeps webhook dispatch off the reversing request's thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReversalPartnerNotifier {

    /** Actor prefix used by {@link PispController} when initiating a partner payment. */
    static final String OB_ACTOR_PREFIX = "open-banking:";

    private final ConsentService consentService;
    private final PartnerWebhookDeliveryService webhookDelivery;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentReversed(PaymentReversedEvent event) {
        String createdBy = event.originalCreatedBy();
        if (createdBy == null || !createdBy.startsWith(OB_ACTOR_PREFIX)) {
            return; // not a partner-initiated (PISP) payment — nothing to notify
        }
        String consentId = createdBy.substring(OB_ACTOR_PREFIX.length());

        UUID org;
        try {
            org = PartnerWebhookDeliveryService.parseOrg(consentService.tppClientIdFor(consentId));
        } catch (RuntimeException e) {
            // Consent could have been deleted, or the id is malformed — never break on notify.
            log.warn("PAYMENT.REVERSED: could not resolve partner org for consent {}: {}",
                    consentId, e.getMessage());
            return;
        }

        if (org != null) {
            webhookDelivery.publishEvent(org, "PAYMENT.REVERSED", Map.of(
                    "paymentId", event.paymentId().toString(),
                    "consentId", consentId,
                    "amount", event.amount(),
                    "reversalReference", event.reversalReference()));
        }
    }
}
