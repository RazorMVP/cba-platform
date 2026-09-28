package com.cba.card.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The compensating control for CVE-2026-41707: DPoP-scheme requests never reach Spring Security. */
@DisplayName("DPoPRejectionFilter — Authorization: DPoP is rejected before Spring Security")
class DPoPRejectionFilterTest {

    private final DPoPRejectionFilter filter = new DPoPRejectionFilter();

    @Test
    @DisplayName("DPoP scheme: 401 DPOP_NOT_SUPPORTED and the chain is not called")
    void dpopRejected() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/accounts");
        req.addHeader("Authorization", "DPoP eyJhbGciOiJSUzI1NiJ9.x.y");
        req.addHeader("DPoP", "eyJ0eXAiOiJkcG9wK2p3dCJ9.a.b");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("DPOP_NOT_SUPPORTED");
        assertThat(res.getHeader("WWW-Authenticate")).startsWith("Bearer");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("scheme match is case-insensitive and ignores leading spaces, like Spring's matcher")
    void caseAndWhitespace() {
        assertThat(rejects("dpop abc")).isTrue();
        assertThat(rejects("  DPOP abc")).isTrue();
    }

    @Test
    @DisplayName("any of several Authorization headers using DPoP is rejected")
    void multipleHeaders() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer abc");
        req.addHeader("Authorization", "DPoP abc");
        assertThat(DPoPRejectionFilter.usesDPoPScheme(req)).isTrue();
    }

    @Test
    @DisplayName("Bearer, ApiKey, Basic and no header pass through to the chain")
    void othersPass() throws Exception {
        assertThat(rejects("Bearer eyJ.x.y")).isFalse();
        assertThat(rejects("ApiKey cba_x")).isFalse();
        assertThat(rejects("Basic dXNlcjpwYXNz")).isFalse();
        assertThat(DPoPRejectionFilter.usesDPoPScheme(new MockHttpServletRequest())).isFalse();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/accounts");
        req.addHeader("Authorization", "Bearer eyJ.x.y");
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    private static boolean rejects(String authorization) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", authorization);
        return DPoPRejectionFilter.usesDPoPScheme(req);
    }
}
