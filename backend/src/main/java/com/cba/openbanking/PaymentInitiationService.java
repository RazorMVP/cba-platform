package com.cba.openbanking;

import com.cba.account.Account;
import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.openbanking.dto.DomesticPaymentRequest;
import com.cba.payment.PaymentService;
import com.cba.payment.dto.PaymentResponse;
import com.cba.payment.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PISP: one payment per consent, exactly as the customer authorised it (UK OB domestic-payment-
 * consent). The consent row is locked for the whole payment, so two concurrent requests under one
 * consent can't both pay: the second waits, then finds it CONSUMED.
 */
@Service
@RequiredArgsConstructor
public class PaymentInitiationService {

    private final ConsentRepository consentRepository;
    private final AccountRepository accountRepository;
    private final PaymentService paymentService;
    private final AuditLogService auditLogService;

    /** The payment, the consent it was made under, and whether this was a retry of a payment already made. */
    public record Initiated(PaymentResponse payment, UsableConsent consent, boolean replay) {}

    /**
     * Pay under the caller's consent:
     * <ol>
     *   <li>the caller's consent (else 404), locked; the source account the consent customer's (else 404);</li>
     *   <li>already CONSUMED: the same request again returns the payment already made ({@code replay});
     *       anything else is 403 {@code CONSENT_CONSUMED};</li>
     *   <li>otherwise AUTHORISED, unexpired, {@code payments} (the usual 403s), with an initiation
     *       (403 {@code CONSENT_HAS_NO_INITIATION} for consents from before V65);</li>
     *   <li>the request matches the initiation (400 {@code PAYMENT_DOES_NOT_MATCH_CONSENT}) and the
     *       source account is in its currency (400 {@code CURRENCY_MISMATCH});</li>
     *   <li>transfer, then mark the consent CONSUMED — same transaction: a refused transfer throws,
     *       everything rolls back, and the consent stays usable.</li>
     * </ol>
     */
    @Transactional
    public Initiated initiate(DomesticPaymentRequest request) {
        String tpp = ConsentService.requireTpp();
        String consentId = request.consentId();
        OpenBankingConsent consent = consentRepository.findByConsentIdForUpdate(consentId)
                .filter(c -> tpp.equals(c.getTppClientId()))
                .orElseThrow(() -> CbaException.notFound("Consent", consentId));

        UUID customerId = consent.getCustomer().getId();
        Account source = accountRepository.findById(request.sourceAccountId())
                .filter(a -> a.getCustomer() != null && customerId.equals(a.getCustomer().getId()))
                .orElseThrow(() -> CbaException.notFound("Account", request.sourceAccountId()));

        if (consent.getStatus() == ConsentStatus.CONSUMED) {
            return replay(consent, request);
        }

        UsableConsent usable = ConsentService.usable(consent, ConsentScope.PAYMENTS);
        if (!consent.hasInitiation()) {
            throw CbaException.forbidden("CONSENT_HAS_NO_INITIATION", "Consent " + consentId
                    + " names no payment; create a new payment consent with an initiation");
        }
        String mismatch = mismatch(consent, request);
        if (mismatch != null) {
            throw CbaException.badRequest("PAYMENT_DOES_NOT_MATCH_CONSENT",
                    "The payment's " + mismatch + " differs from what consent " + consentId + " authorises");
        }
        if (!consent.getInstructedCurrency().equalsIgnoreCase(source.getCurrencyCode())) {
            throw CbaException.badRequest("CURRENCY_MISMATCH", "Source account is in "
                    + source.getCurrencyCode() + "; the consent authorises " + consent.getInstructedCurrency());
        }

        String description = consent.getPaymentReference() != null ? consent.getPaymentReference() : request.reference();
        PaymentResponse payment = paymentService.transfer(
                new TransferRequest(source.getId(), consent.getCreditorAccountId(),
                        consent.getInstructedAmount(), description, null),
                PaymentReversalPartnerNotifier.OB_ACTOR_PREFIX + consentId);

        consent.setStatus(ConsentStatus.CONSUMED);
        consent.setPaymentId(payment.id());
        consent.setConsumedAt(Instant.now());
        consentRepository.save(consent);
        auditLogService.log("OpenBankingConsent", consentId, "CONSUME", null,
                Map.of("status", "CONSUMED", "paymentId", payment.id().toString()));
        return new Initiated(payment, usable, false);
    }

    /** A retry of the payment already made returns it; any other request on a used consent is refused. */
    private Initiated replay(OpenBankingConsent consent, DomesticPaymentRequest request) {
        if (consent.getPaymentId() != null && mismatch(consent, request) == null) {
            PaymentResponse original = paymentService.getPayment(consent.getPaymentId());
            if (request.sourceAccountId().equals(original.sourceAccountId())) {
                UsableConsent used = new UsableConsent(consent.getConsentId(), consent.getTppClientId(),
                        consent.getCustomer().getId(),
                        consent.getScopes() == null ? Set.of() : Set.copyOf(consent.getScopes()));
                return new Initiated(original, used, true);
            }
        }
        throw CbaException.forbidden("CONSENT_CONSUMED",
                "Consent " + consent.getConsentId() + " has already been used for its payment");
    }

    /** The first field of the request that differs from the consent's initiation, or null if it matches. */
    static String mismatch(OpenBankingConsent consent, DomesticPaymentRequest request) {
        if (!consent.hasInitiation()) {
            return null;
        }
        if (consent.getInstructedAmount().compareTo(request.amount()) != 0) {
            return "amount";
        }
        if (!consent.getInstructedCurrency().equalsIgnoreCase(request.currency())) {
            return "currency";
        }
        if (!consent.getCreditorAccountId().equals(request.destinationAccountId())) {
            return "destinationAccountId";
        }
        if (consent.getDebtorAccountId() != null && !consent.getDebtorAccountId().equals(request.sourceAccountId())) {
            return "sourceAccountId";
        }
        if (consent.getPaymentReference() != null && request.reference() != null
                && !consent.getPaymentReference().equals(request.reference())) {
            return "reference";
        }
        return null;
    }
}
