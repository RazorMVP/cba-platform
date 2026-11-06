package com.cba.openbanking;

import com.cba.common.exception.CbaException;
import com.cba.customer.Customer;
import com.cba.customer.CustomerRepository;
import com.cba.openbanking.dto.ConsentResponse;
import com.cba.openbanking.dto.StaffConsentResponse;
import com.cba.partner.PartnerOrganization;
import com.cba.partner.PartnerOrganizationRepository;
import com.cba.partner.PartnerWebhookDeliveryService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bank staff's view of Open Banking consents: list and look up any consent, and revoke one on the
 * customer's behalf. Authorising stays the customer's own step ({@code PUT /open-banking/v3.1/consents/{id}/authorise}).
 */
@Service
@RequiredArgsConstructor
public class StaffConsentService {

    private final ConsentRepository consentRepository;
    private final ConsentService consentService;
    private final CustomerRepository customerRepository;
    private final PartnerOrganizationRepository partnerOrganizationRepository;

    /** PISP = has {@code payments}; CBPII = has {@code fundsconfirmation}; AISP = everything else. */
    static String typeOf(Collection<String> scopes) {
        if (scopes == null) {
            return "AISP";
        }
        if (scopes.contains(ConsentScope.PAYMENTS.value())) {
            return "PISP";
        }
        if (scopes.contains(ConsentScope.FUNDS_CONFIRMATION.value())) {
            return "CBPII";
        }
        return "AISP";
    }

    @Transactional(readOnly = true)
    public Page<StaffConsentResponse> list(UUID customerId, String tppClientId, ConsentStatus status,
                                           String type, Pageable pageable) {
        Specification<OpenBankingConsent> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (customerId != null) {
                where.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (tppClientId != null && !tppClientId.isBlank()) {
                where.add(cb.equal(root.get("tppClientId"), tppClientId));
            }
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (type != null && !type.isBlank()) {
                var scopes = root.<Collection<String>>get("scopes");
                switch (type.toUpperCase(Locale.ROOT)) {
                    case "PISP" -> where.add(cb.isMember(ConsentScope.PAYMENTS.value(), scopes));
                    case "CBPII" -> where.add(cb.isMember(ConsentScope.FUNDS_CONFIRMATION.value(), scopes));
                    case "AISP" -> {
                        where.add(cb.isNotMember(ConsentScope.PAYMENTS.value(), scopes));
                        where.add(cb.isNotMember(ConsentScope.FUNDS_CONFIRMATION.value(), scopes));
                    }
                    default -> throw CbaException.badRequest("INVALID_CONSENT_TYPE",
                            "type must be AISP, PISP or CBPII");
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<OpenBankingConsent> page = consentRepository.findAll(spec, pageable);
        Map<UUID, String> customers = customerNames(page.getContent());
        Map<String, String> tpps = tppNames(page.getContent());
        return page.map(c -> toResponse(c, customers, tpps));
    }

    @Transactional(readOnly = true)
    public StaffConsentResponse get(String consentId) {
        OpenBankingConsent consent = find(consentId);
        return toResponse(consent, customerNames(List.of(consent)), tppNames(List.of(consent)));
    }

    /** Revoke on the customer's behalf — audited with the reason, {@code CONSENT.REVOKED} sent to the TPP. */
    @Transactional
    public StaffConsentResponse revoke(String consentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw CbaException.badRequest("REASON_REQUIRED", "A reason is required to revoke a consent for a customer");
        }
        OpenBankingConsent revoked = consentService.revoke(find(consentId), reason.trim());
        return toResponse(revoked, customerNames(List.of(revoked)), tppNames(List.of(revoked)));
    }

    private OpenBankingConsent find(String consentId) {
        return consentRepository.findByConsentId(consentId)
                .orElseThrow(() -> CbaException.notFound("Consent", consentId));
    }

    /**
     * Customer display names, loaded through {@link CustomerRepository} (the encrypted name columns
     * decrypt only when the entity is loaded there, not through a join from another repository).
     */
    private Map<UUID, String> customerNames(List<OpenBankingConsent> consents) {
        List<UUID> ids = consents.stream().map(c -> c.getCustomer().getId()).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (Customer c : customerRepository.findAllById(ids)) {
            names.put(c.getId(), (Objects.toString(c.getFirstName(), "") + " "
                    + Objects.toString(c.getLastName(), "")).trim());
        }
        return names;
    }

    /** TPP names: a consent's {@code tppClientId} is its partner organisation's id. */
    private Map<String, String> tppNames(List<OpenBankingConsent> consents) {
        List<UUID> orgIds = consents.stream()
                .map(c -> PartnerWebhookDeliveryService.parseOrg(c.getTppClientId()))
                .filter(Objects::nonNull).distinct().toList();
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        return partnerOrganizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(o -> o.getId().toString(), PartnerOrganization::getName,
                        (a, b) -> a));
    }

    private static StaffConsentResponse toResponse(OpenBankingConsent c, Map<UUID, String> customers,
                                                   Map<String, String> tpps) {
        UUID customerId = c.getCustomer().getId();
        List<String> scopes = c.getScopes() == null ? List.of() : List.copyOf(c.getScopes());
        return new StaffConsentResponse(
                c.getConsentId(), typeOf(scopes), customerId, customers.get(customerId),
                c.getTppClientId(), tpps.get(c.getTppClientId()), scopes, c.getStatus(),
                c.getExpiryDate(), c.getCreatedAt(), c.getUpdatedAt(),
                ConsentResponse.initiationOf(c), c.getPaymentId(), c.getConsumedAt());
    }
}
