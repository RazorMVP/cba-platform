package com.cba.partner;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A suspended partner organisation is refused by both partner authentication filters, whatever its
 * credentials say (backlog 23b). Before, {@code SUSPENDED} existed but neither filter looked at it.
 */
@DisplayName("Partner auth filters — a suspended organisation authenticates nothing")
class PartnerSuspensionFiltersTest {

    PartnerApiKeyRepository apiKeyRepo = mock(PartnerApiKeyRepository.class);
    PartnerJwtService jwtService = mock(PartnerJwtService.class);
    PartnerOrganizationRepository orgRepo = mock(PartnerOrganizationRepository.class);

    UUID orgId = UUID.randomUUID();
    PartnerOrganization org;

    @BeforeEach
    void setUp() {
        org = new PartnerOrganization();
        org.setId(orgId);
        org.setStatus(PartnerStatus.PRODUCTION);
        org.setTier("PRO");
        org.setEnvironment(PartnerEnvironment.PRODUCTION);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private PartnerApiKey key() {
        PartnerApiKey k = new PartnerApiKey();
        k.setOrganization(org);
        k.setScopes(List.of("accounts"));
        k.setActive(true);
        return k;
    }

    private void apiKeyRequest() throws Exception {
        when(apiKeyRepo.findByKeyHashAndActiveTrue(any())).thenReturn(Optional.of(key()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open-banking/v3.1/aisp/accounts");
        request.addHeader("Authorization", "ApiKey cba_test-key");
        new PartnerApiKeyAuthFilter(apiKeyRepo).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private void jwtRequest() throws Exception {
        when(jwtService.verify("tok")).thenReturn(new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("orgId", orgId.toString())
                .claim("role", "DEVELOPER")
                .claim("status", "PRODUCTION")   // the token's claim says active; the org isn't
                .build());
        when(orgRepo.findById(orgId)).thenReturn(Optional.of(org));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partners/" + orgId + "/consents");
        request.addHeader("Authorization", "Bearer tok");
        new PartnerJwtFilter(jwtService, orgRepo).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    @DisplayName("API key of an active org: authenticated as API_CLIENT")
    void apiKey_active() throws Exception {
        apiKeyRequest();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(orgId.toString());
    }

    @Test
    @DisplayName("API key of a suspended org: not authenticated (the endpoint answers 401), last-used not touched")
    void apiKey_suspended() throws Exception {
        org.setStatus(PartnerStatus.SUSPENDED);
        apiKeyRequest();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(apiKeyRepo, never()).save(any());
    }

    @Test
    @DisplayName("partner JWT of an active org: authenticated")
    void jwt_active() throws Exception {
        jwtRequest();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("partner JWT whose claim still says PRODUCTION, org now SUSPENDED: not authenticated")
    void jwt_suspended() throws Exception {
        org.setStatus(PartnerStatus.SUSPENDED);
        jwtRequest();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
