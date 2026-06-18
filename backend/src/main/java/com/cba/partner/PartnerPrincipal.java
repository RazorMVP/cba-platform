package com.cba.partner;

import java.util.List;

/**
 * Authentication details for a partner request authenticated by API key.
 *
 * <p>Placed on the Spring {@code Authentication.getDetails()} by {@link PartnerApiKeyAuthFilter}
 * so that {@link PartnerSecurity} can resolve the owning {@code orgId} the same way it does for
 * partner JWTs (which carry the claim on a Nimbus {@code JWTClaimsSet}).
 */
public record PartnerPrincipal(
        String orgId,
        String role,
        String environment,
        String tier,
        List<String> scopes
) {}
