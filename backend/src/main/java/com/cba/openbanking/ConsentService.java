package com.cba.openbanking;

import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.card.CardServiceClient;
import com.cba.openbanking.dto.*;
import com.cba.partner.PartnerWebhookDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository    consentRepository;
    private final CustomerRepository   customerRepository;
    private final AccountRepository    accountRepository;
    private final AuditLogService      auditLogService;
    private final CardServiceClient    cardServiceClient;
    private final PartnerWebhookDeliveryService webhookDelivery;

    // ── Consent lifecycle ────────────────────────────────────────────

    @Transactional
    public ConsentResponse createConsent(ConsentRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> CbaException.notFound("Customer", request.customerId()));

        OpenBankingConsent consent = new OpenBankingConsent();
        consent.setConsentId("ob-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        consent.setCustomer(customer);
        consent.setTppClientId(request.tppClientId());
        consent.setScopes(request.scopes());
        consent.setStatus(ConsentStatus.AWAITING_AUTHORISATION);
        consent.setExpiryDate(request.expiryDate());

        OpenBankingConsent saved = consentRepository.save(consent);
        auditLogService.log("OpenBankingConsent", saved.getConsentId(), "CREATE", null, saved);
        publishToPartner(saved, "CONSENT.CREATED",
                Map.of("consentId", saved.getConsentId(),
                       "scopes", saved.getScopes() == null ? List.of() : saved.getScopes()));
        return ConsentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ConsentResponse getConsent(String consentId) {
        return ConsentResponse.from(findByConsentId(consentId));
    }

    /**
     * Authorise a consent — simulates the customer approving the TPP's access request.
     * In a full FAPI 2.0 flow the redirect back from Keycloak triggers this; here it is
     * called directly (Option A — API-only, no OAuth redirect in backend).
     */
    @Transactional
    public ConsentResponse authoriseConsent(String consentId) {
        OpenBankingConsent consent = findByConsentId(consentId);

        if (consent.getStatus() != ConsentStatus.AWAITING_AUTHORISATION) {
            throw CbaException.conflict("CONSENT_NOT_AWAITING",
                    "Consent " + consentId + " cannot be authorised in status " + consent.getStatus());
        }

        consent.setStatus(ConsentStatus.AUTHORISED);
        OpenBankingConsent saved = consentRepository.save(consent);
        auditLogService.log("OpenBankingConsent", consentId, "AUTHORISE", null, saved);
        publishToPartner(saved, "CONSENT.AUTHORISED", Map.of("consentId", consentId));
        return ConsentResponse.from(saved);
    }

    @Transactional
    public ConsentResponse revokeConsent(String consentId) {
        OpenBankingConsent consent = findByConsentId(consentId);

        if (consent.getStatus() == ConsentStatus.REVOKED) {
            throw CbaException.conflict("CONSENT_ALREADY_REVOKED", "Consent " + consentId + " is already revoked");
        }

        consent.setStatus(ConsentStatus.REVOKED);
        OpenBankingConsent saved = consentRepository.save(consent);
        auditLogService.log("OpenBankingConsent", consentId, "REVOKE", null, saved);
        publishToPartner(saved, "CONSENT.REVOKED", Map.of("consentId", consentId));
        return ConsentResponse.from(saved);
    }

    // ── PISP: Domestic Payment Initiation ────────────────────────────

    @Transactional(readOnly = true)
    public void validatePispConsent(String consentId) {
        OpenBankingConsent consent = findByConsentId(consentId);
        if (consent.getStatus() != ConsentStatus.AUTHORISED) {
            throw CbaException.forbidden("Consent " + consentId + " is not authorised for payment initiation");
        }
        if (!consent.getScopes().contains("payments")) {
            throw CbaException.forbidden("Consent does not include 'payments' scope");
        }
        if (consent.getExpiryDate() != null && consent.getExpiryDate().isBefore(Instant.now())) {
            throw CbaException.badRequest("CONSENT_EXPIRED", "Consent " + consentId + " has expired");
        }
    }

    // ── CBPII: Funds Confirmation ────────────────────────────────────

    public FundsConfirmationResponse confirmFunds(FundsConfirmationRequest request) {
        OpenBankingConsent consent = findByConsentId(request.consentId());

        if (consent.getStatus() != ConsentStatus.AUTHORISED) {
            throw CbaException.forbidden("Consent " + request.consentId() + " is not authorised");
        }
        if (!consent.getScopes().contains(ConsentScope.FUNDS_CONFIRMATION.value())
                && !consent.getScopes().contains("fundsconfirmation")) {
            throw CbaException.forbidden("Consent does not include 'fundsconfirmation' scope");
        }
        if (consent.getExpiryDate() != null && consent.getExpiryDate().isBefore(Instant.now())) {
            throw CbaException.badRequest("CONSENT_EXPIRED", "Consent has expired");
        }

        // 1. Try bank account (monolith)
        var accountOpt = accountRepository.findById(request.accountId());
        if (accountOpt.isPresent()) {
            boolean fundsAvailable = accountOpt.get().getBalance().compareTo(request.amount()) >= 0;
            publishToPartner(consent, "FUNDS.CONFIRMED", Map.of(
                    "consentId", consent.getConsentId(),
                    "accountId", request.accountId().toString(),
                    "fundsAvailable", fundsAvailable));
            return buildFundsConfirmationResponse(request, fundsAvailable);
        }

        // 2. Fall back to card available balance (card-service)
        //    Requires CARD_READ or CARD_BALANCES_READ scope in addition to fundsconfirmation
        boolean hasCardScope = consent.getScopes().contains(ConsentScope.CARD_READ.value())
                || consent.getScopes().contains(ConsentScope.CARD_BALANCES_READ.value());
        if (hasCardScope) {
            var balOpt = cardServiceClient.getCardBalance(request.accountId());
            if (balOpt.isPresent() && balOpt.get().availableBalance() != null) {
                boolean fundsAvailable = balOpt.get().availableBalance().compareTo(request.amount()) >= 0;
                publishToPartner(consent, "FUNDS.CONFIRMED", Map.of(
                        "consentId", consent.getConsentId(),
                        "accountId", request.accountId().toString(),
                        "fundsAvailable", fundsAvailable));
                return buildFundsConfirmationResponse(request, fundsAvailable);
            }
        }

        throw CbaException.notFound("Account", request.accountId());
    }

    private FundsConfirmationResponse buildFundsConfirmationResponse(
            FundsConfirmationRequest request, boolean fundsAvailable) {
        return new FundsConfirmationResponse(
                "fc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
                request.consentId(),
                fundsAvailable,
                request.accountId(),
                request.amount(),
                request.currency(),
                Instant.now()
        );
    }

    // ── Private helpers ──────────────────────────────────────────────

    /** Returns the partner orgId (tppClientId) that owns a consent — used by PISP to attribute payment events. */
    @Transactional(readOnly = true)
    public String tppClientIdFor(String consentId) {
        return findByConsentId(consentId).getTppClientId();
    }

    /** Publish a partner webhook event for the org that owns this consent (no-op if not a partner org). */
    private void publishToPartner(OpenBankingConsent consent, String eventType, Object payload) {
        UUID org = PartnerWebhookDeliveryService.parseOrg(consent.getTppClientId());
        if (org != null) {
            webhookDelivery.publishEvent(org, eventType, payload);
        }
    }

    private OpenBankingConsent findByConsentId(String consentId) {
        return consentRepository.findByConsentId(consentId)
                .orElseThrow(() -> CbaException.notFound("Consent", consentId));
    }
}
