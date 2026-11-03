package com.cba.openbanking;

import com.cba.common.exception.CbaException;

import java.util.Set;
import java.util.UUID;

/**
 * An authorised, unexpired consent owned by the calling TPP, copied out of its transaction so callers
 * never touch the lazy customer or scope collection. Returned by {@link ConsentService#requireUsableConsent}.
 */
public record UsableConsent(String consentId, String tppClientId, UUID customerId, Set<String> scopes) {

    public boolean has(ConsentScope scope) {
        return scopes.contains(scope.value());
    }

    /** 403 {@code CONSENT_SCOPE_MISSING} unless the consent grants {@code scope}. */
    public void require(ConsentScope scope) {
        if (!has(scope)) {
            throw CbaException.forbidden("CONSENT_SCOPE_MISSING",
                    "Consent " + consentId + " does not grant '" + scope.value() + "'");
        }
    }
}
