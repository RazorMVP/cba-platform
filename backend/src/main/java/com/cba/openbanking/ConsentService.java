package com.cba.openbanking;

import com.cba.account.AccountRepository;
import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import com.cba.config.CustomerAccess;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.card.CardServiceClient;
import com.cba.openbanking.dto.*;
import com.cba.partner.PartnerSecurity;
import com.cba.partner.PartnerWebhookDeliveryService;
import com.cba.payment.Payment;
import com.cba.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Consent lifecycle and the consent checks every Open Banking data call goes through.
 *
 * <p>A consent binds three things: the TPP that created it ({@code tppClientId} = the partner
 * organisation of the API key that called {@code POST /consents}), the customer whose data it
 * covers, and the scopes that customer authorised. A TPP reaches customer data only through
 * {@link #requireUsableConsent}: its own consent, AUTHORISED, unexpired, with the scope. A consent
 * the caller isn't party to is a 404, the same as one that doesn't exist (anti-enumeration).
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository    consentRepository;
    private final CustomerRepository   customerRepository;
    private final AccountRepository    accountRepository;
    private final AuditLogService      auditLogService;
    private final CardServiceClient    cardServiceClient;
    private final PartnerWebhookDeliveryService webhookDelivery;
    private final CustomerAccess       customerAccess;
    private final PaymentRepository    paymentRepository;

    // ── Consent lifecycle ────────────────────────────────────────────

    @Transactional
    public ConsentResponse createConsent(ConsentRequest request) {
        String tpp = requireTpp();
        if (request.tppClientId() != null && !request.tppClientId().isBlank()
                && !tpp.equals(request.tppClientId())) {
            throw CbaException.badRequest("TPP_CLIENT_MISMATCH",
                    "tppClientId must be the calling partner organisation (" + tpp + ") or omitted");
        }
        List<String> scopes = catalogueScopes(request.scopes());
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> CbaException.notFound("Customer", request.customerId()));

        OpenBankingConsent consent = new OpenBankingConsent();
        consent.setConsentId("ob-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        consent.setCustomer(customer);
        consent.setTppClientId(tpp);
        consent.setScopes(scopes);
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
        OpenBankingConsent consent = findByConsentId(consentId);
        requireParty(consent);
        return ConsentResponse.from(consent);
    }

    /**
     * Authorise a consent — simulates the customer approving the TPP's access request.
     * In a full FAPI 2.0 flow the redirect back from Keycloak triggers this; here it is
     * called directly (Option A — API-only, no OAuth redirect in backend). Only the consent's own
     * customer can authorise it; anyone else gets a 404.
     */
    @Transactional
    public ConsentResponse authoriseConsent(String consentId) {
        OpenBankingConsent consent = findByConsentId(consentId);
        UUID caller = customerAccess.callerCustomerId().orElse(null);
        if (caller == null || !caller.equals(consent.getCustomer().getId())) {
            throw CbaException.notFound("Consent", consentId);
        }

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

    /** Statuses that can still expire; REVOKED and EXPIRED are terminal. */
    private static final List<ConsentStatus> EXPIRABLE_STATUSES =
            List.of(ConsentStatus.AWAITING_AUTHORISATION, ConsentStatus.AUTHORISED);

    /**
     * Expire every consent whose {@code expiryDate} has passed, transitioning it to
     * {@link ConsentStatus#EXPIRED}, auditing the change, and firing the
     * {@code CONSENT.EXPIRED} partner webhook. Idempotent — a consent already REVOKED/EXPIRED
     * is never re-processed (excluded by the query). Driven by {@link ConsentExpiryJob}.
     *
     * @param now the cutoff instant (injected for testability)
     * @return the number of consents expired this run
     */
    @Transactional
    public int expireDueConsents(Instant now) {
        List<OpenBankingConsent> due =
                consentRepository.findByStatusInAndExpiryDateBefore(EXPIRABLE_STATUSES, now);
        for (OpenBankingConsent consent : due) {
            consent.setStatus(ConsentStatus.EXPIRED);
            OpenBankingConsent saved = consentRepository.save(consent);
            auditLogService.log("OpenBankingConsent", saved.getConsentId(), "EXPIRE", null, saved);
            publishToPartner(saved, "CONSENT.EXPIRED", Map.of("consentId", saved.getConsentId()));
        }
        return due.size();
    }

    @Transactional
    public ConsentResponse revokeConsent(String consentId) {
        OpenBankingConsent consent = findByConsentId(consentId);
        requireParty(consent);

        if (consent.getStatus() == ConsentStatus.REVOKED) {
            throw CbaException.conflict("CONSENT_ALREADY_REVOKED", "Consent " + consentId + " is already revoked");
        }

        consent.setStatus(ConsentStatus.REVOKED);
        OpenBankingConsent saved = consentRepository.save(consent);
        auditLogService.log("OpenBankingConsent", consentId, "REVOKE", null, saved);
        publishToPartner(saved, "CONSENT.REVOKED", Map.of("consentId", consentId));
        return ConsentResponse.from(saved);
    }

    // ── Consent checks for data calls ────────────────────────────────

    /**
     * The calling TPP's consent, usable now: owned by the caller's partner organisation (else 404
     * {@code CONSENT_NOT_FOUND}), AUTHORISED ({@code CONSENT_REVOKED} / {@code CONSENT_EXPIRED} /
     * {@code CONSENT_NOT_AUTHORISED}, all 403), not past its expiry date, and granting at least one
     * of {@code anyOf} (403 {@code CONSENT_SCOPE_MISSING}; none given = no scope check here).
     */
    @Transactional(readOnly = true)
    public UsableConsent requireUsableConsent(String consentId, ConsentScope... anyOf) {
        String tpp = requireTpp();
        OpenBankingConsent consent = consentRepository.findByConsentId(consentId)
                .filter(c -> tpp.equals(c.getTppClientId()))
                .orElseThrow(() -> CbaException.notFound("Consent", consentId));

        switch (consent.getStatus()) {
            case REVOKED -> throw CbaException.forbidden("CONSENT_REVOKED", "Consent " + consentId + " has been revoked");
            case EXPIRED -> throw CbaException.forbidden("CONSENT_EXPIRED", "Consent " + consentId + " has expired");
            case AWAITING_AUTHORISATION -> throw CbaException.forbidden("CONSENT_NOT_AUTHORISED",
                    "Consent " + consentId + " has not been authorised by the customer");
            case AUTHORISED -> { }
        }
        if (consent.getExpiryDate() != null && !consent.getExpiryDate().isAfter(Instant.now())) {
            throw CbaException.forbidden("CONSENT_EXPIRED", "Consent " + consentId + " has expired");
        }

        UsableConsent usable = new UsableConsent(consent.getConsentId(), consent.getTppClientId(),
                consent.getCustomer().getId(),
                consent.getScopes() == null ? Set.of() : Set.copyOf(consent.getScopes()));
        if (anyOf.length > 0 && Arrays.stream(anyOf).noneMatch(usable::has)) {
            throw CbaException.forbidden("CONSENT_SCOPE_MISSING", "Consent " + consentId + " grants none of "
                    + Arrays.stream(anyOf).map(ConsentScope::value).toList());
        }
        return usable;
    }

    // ── PISP: Domestic Payment Initiation ────────────────────────────

    /**
     * A payment may be initiated under the consent only from an account of the consent's customer;
     * any other source account is a 404, checked before any money moves.
     */
    @Transactional(readOnly = true)
    public UsableConsent authorisePaymentInitiation(String consentId, UUID sourceAccountId) {
        UsableConsent consent = requireUsableConsent(consentId, ConsentScope.PAYMENTS);
        boolean own = accountRepository.findById(sourceAccountId)
                .map(a -> consent.customerId().equals(a.getCustomer().getId()))
                .orElse(false);
        if (!own) {
            throw CbaException.notFound("Account", sourceAccountId);
        }
        return consent;
    }

    /**
     * The consent a PISP payment was made under, if the calling TPP owns that consent; otherwise
     * 404 {@code PAYMENT_NOT_FOUND}. PISP payments record {@code open-banking:<consentId>} as
     * their creator, so payments made any other way are never visible here.
     */
    @Transactional(readOnly = true)
    public String requireOwnPayment(UUID paymentId) {
        String tpp = requireTpp();
        String createdBy = paymentRepository.findById(paymentId).map(Payment::getCreatedBy).orElse(null);
        if (createdBy != null && createdBy.startsWith(PaymentReversalPartnerNotifier.OB_ACTOR_PREFIX)) {
            String consentId = createdBy.substring(PaymentReversalPartnerNotifier.OB_ACTOR_PREFIX.length());
            boolean own = consentRepository.findByConsentId(consentId)
                    .map(c -> tpp.equals(c.getTppClientId()))
                    .orElse(false);
            if (own) {
                return consentId;
            }
        }
        throw CbaException.notFound("Payment", paymentId);
    }

    // ── CBPII: Funds Confirmation ────────────────────────────────────

    /**
     * Funds check under a {@code fundsconfirmation} consent, for an account or card of the consent's
     * customer only (anything else is a 404). A card also needs {@code card_read} or
     * {@code card_balances_read}.
     */
    @Transactional(readOnly = true)
    public FundsConfirmationResponse confirmFunds(FundsConfirmationRequest request) {
        UsableConsent consent = requireUsableConsent(request.consentId(), ConsentScope.FUNDS_CONFIRMATION);

        // 1. Bank account (monolith)
        var accountOpt = accountRepository.findById(request.accountId());
        if (accountOpt.isPresent()) {
            if (!consent.customerId().equals(accountOpt.get().getCustomer().getId())) {
                throw CbaException.notFound("Account", request.accountId());
            }
            boolean fundsAvailable = accountOpt.get().getBalance().compareTo(request.amount()) >= 0;
            publishFundsConfirmed(consent, request, fundsAvailable);
            return buildFundsConfirmationResponse(request, fundsAvailable);
        }

        // 2. Card (card-service): must be the consent customer's card, and the consent must cover cards
        var cardOpt = cardServiceClient.getCard(request.accountId());
        if (cardOpt.isPresent() && consent.customerId().equals(cardOpt.get().customerId())) {
            if (!consent.has(ConsentScope.CARD_READ) && !consent.has(ConsentScope.CARD_BALANCES_READ)) {
                throw CbaException.forbidden("CONSENT_SCOPE_MISSING", "Consent " + consent.consentId()
                        + " grants neither 'card_read' nor 'card_balances_read'");
            }
            var balOpt = cardServiceClient.getCardBalance(request.accountId());
            if (balOpt.isPresent() && balOpt.get().availableBalance() != null) {
                boolean fundsAvailable = balOpt.get().availableBalance().compareTo(request.amount()) >= 0;
                publishFundsConfirmed(consent, request, fundsAvailable);
                return buildFundsConfirmationResponse(request, fundsAvailable);
            }
        }

        throw CbaException.notFound("Account", request.accountId());
    }

    private void publishFundsConfirmed(UsableConsent consent, FundsConfirmationRequest request,
                                       boolean fundsAvailable) {
        UUID org = PartnerWebhookDeliveryService.parseOrg(consent.tppClientId());
        if (org != null) {
            webhookDelivery.publishEvent(org, "FUNDS.CONFIRMED", Map.of(
                    "consentId", consent.consentId(),
                    "accountId", request.accountId().toString(),
                    "fundsAvailable", fundsAvailable));
        }
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

    /** The calling TPP's partner organisation id; 403 {@code TPP_UNIDENTIFIED} if the caller has none. */
    String requireTpp() {
        UUID org = PartnerSecurity.currentOrgId();
        if (org == null) {
            throw CbaException.forbidden("TPP_UNIDENTIFIED",
                    "Open Banking TPP calls must authenticate with a partner API key");
        }
        return org.toString();
    }

    /** 404 unless the caller is the consent's TPP or the consent's customer. */
    private void requireParty(OpenBankingConsent consent) {
        UUID org = PartnerSecurity.currentOrgId();
        if (org != null && org.toString().equals(consent.getTppClientId())) {
            return;
        }
        Optional<UUID> customer = customerAccess.callerCustomerId();
        if (customer.isPresent() && customer.get().equals(consent.getCustomer().getId())) {
            return;
        }
        throw CbaException.notFound("Consent", consent.getConsentId());
    }

    /** The requested scopes, de-duplicated; 400 {@code INVALID_SCOPE} if any isn't in {@link ConsentScope}. */
    private static List<String> catalogueScopes(List<String> requested) {
        List<String> unknown = requested.stream()
                .filter(s -> ConsentScope.fromValue(s).isEmpty()).toList();
        if (!unknown.isEmpty()) {
            throw CbaException.badRequest("INVALID_SCOPE", "Unknown scope(s) " + unknown + "; allowed: "
                    + Arrays.stream(ConsentScope.values()).map(ConsentScope::value).toList());
        }
        return List.copyOf(new LinkedHashSet<>(requested));
    }

    private OpenBankingConsent findByConsentId(String consentId) {
        return consentRepository.findByConsentId(consentId)
                .orElseThrow(() -> CbaException.notFound("Consent", consentId));
    }
}
