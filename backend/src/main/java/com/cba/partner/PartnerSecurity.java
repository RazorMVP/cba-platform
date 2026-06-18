package com.cba.partner;

import com.cba.common.exception.CbaException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Authorization guard for partner self-service endpoints.
 *
 * <p>Partner tokens carry an {@code orgId} claim (placed on the authentication details by
 * {@link PartnerJwtFilter}). These helpers enforce that a partner can only act on its own
 * organization/user — preventing IDOR via the {@code {orgId}}/{@code {userId}} path variables.
 *
 * <p>A principal holding {@code ROLE_ADMIN} (Keycloak bank staff, or the dev-auth-bypass
 * filter in local development) is treated as a full-access override.
 */
public final class PartnerSecurity {

    private PartnerSecurity() {}

    /** Require that the caller owns {@code orgId}, or is staff. Throws 404 on mismatch (anti-enumeration). */
    public static void requireOrgAccess(UUID orgId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isStaff(auth)) return;
        String tokenOrgId = orgIdOf(auth);
        if (tokenOrgId == null || orgId == null || !tokenOrgId.equals(orgId.toString())) {
            throw CbaException.notFound("PARTNER_NOT_FOUND", "Partner not found");
        }
    }

    /** Require that the caller is the user identified by {@code userId}, or is staff. Throws 404 on mismatch. */
    public static void requireUserAccess(UUID userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isStaff(auth)) return;
        if (auth == null || userId == null || !userId.toString().equals(auth.getName())) {
            throw CbaException.notFound("USER_NOT_FOUND", "User not found");
        }
    }

    /** The current authenticated partner's organization id, or null if the caller is not a partner. */
    public static UUID currentOrgId() {
        String orgId = orgIdOf(SecurityContextHolder.getContext().getAuthentication());
        if (orgId == null) return null;
        try {
            return UUID.fromString(orgId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isStaff(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Resolves the caller's organization id from a partner JWT (claims) or an API-key principal. */
    private static String orgIdOf(Authentication auth) {
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof JWTClaimsSet claims) {
            Object v = claims.getClaim("orgId");
            return v != null ? v.toString() : null;
        }
        if (details instanceof PartnerPrincipal pp) {
            return pp.orgId();
        }
        return null;
    }
}
