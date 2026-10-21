package com.cba.partner;

import java.util.List;

/**
 * Authentication details for a partner request authenticated by API key.
 *
 * <p>Placed on the Spring {@code Authentication.getDetails()} by {@link PartnerApiKeyAuthFilter}
 * so that {@link PartnerSecurity} can resolve the owning {@code orgId} the same way it does for
 * partner JWTs (which carry the claim on a Nimbus {@code JWTClaimsSet}).
 *
 * <p>{@code tier} and {@code environment} are the <b>organisation's</b>; {@code RateLimitFilter}
 * uses them for the partner's rate limit.
 */
public record PartnerPrincipal(
        String orgId,
        String role,
        String environment,
        String tier,
        List<String> scopes
) {}
