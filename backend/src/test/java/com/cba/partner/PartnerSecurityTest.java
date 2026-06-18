package com.cba.partner;

import com.cba.common.exception.CbaException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PartnerSecurity — org/user ownership guard")
class PartnerSecurityTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void setPartnerAuth(String subject, String orgId) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("orgId", orgId)
                .build();
        var auth = new UsernamePasswordAuthenticationToken(
                subject, null, List.of(new SimpleGrantedAuthority("ROLE_PARTNER_DEVELOPER")));
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setStaffAuth() {
        var auth = new UsernamePasswordAuthenticationToken(
                "staff", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("requireOrgAccess passes when token orgId matches path orgId")
    void orgAccess_match_ok() {
        UUID orgId = UUID.randomUUID();
        setPartnerAuth(UUID.randomUUID().toString(), orgId.toString());
        assertThatCode(() -> PartnerSecurity.requireOrgAccess(orgId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireOrgAccess throws when token orgId differs (IDOR blocked)")
    void orgAccess_mismatch_throws() {
        setPartnerAuth(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertThatThrownBy(() -> PartnerSecurity.requireOrgAccess(UUID.randomUUID()))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("staff ROLE_ADMIN bypasses the org ownership check")
    void orgAccess_staff_override() {
        setStaffAuth();
        assertThatCode(() -> PartnerSecurity.requireOrgAccess(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("requireOrgAccess resolves orgId from an API-key PartnerPrincipal")
    void orgAccess_apiKeyPrincipal() {
        UUID orgId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(
                orgId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_PARTNER_DEVELOPER")));
        auth.setDetails(new PartnerPrincipal(orgId.toString(), "DEVELOPER", "SANDBOX", "BASIC", List.of()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatCode(() -> PartnerSecurity.requireOrgAccess(orgId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PartnerSecurity.requireOrgAccess(UUID.randomUUID()))
                .isInstanceOf(CbaException.class);
    }

    @Test
    @DisplayName("requireUserAccess passes for own userId and throws for another")
    void userAccess() {
        UUID userId = UUID.randomUUID();
        setPartnerAuth(userId.toString(), UUID.randomUUID().toString());
        assertThatCode(() -> PartnerSecurity.requireUserAccess(userId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PartnerSecurity.requireUserAccess(UUID.randomUUID()))
                .isInstanceOf(CbaException.class);
    }
}
