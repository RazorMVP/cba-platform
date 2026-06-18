package com.cba.partner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Meters partner API traffic. Runs in {@code afterCompletion} (inside the security filter
 * chain, so the partner identity is still on the SecurityContext) and records one usage
 * event per request that belongs to a partner (JWT or API key). Non-partner traffic
 * (bank staff, customers) is ignored.
 */
@Component
@RequiredArgsConstructor
public class PartnerUsageInterceptor implements HandlerInterceptor {

    private static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final PartnerUsageRecorder recorder;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UUID orgId = PartnerSecurity.currentOrgId();
        if (orgId == null) return;
        String endpoint = request.getMethod() + " " + normalize(request.getRequestURI());
        recorder.record(orgId, endpoint, response.getStatus());
    }

    /** Collapse UUID path segments to {id} so endpoint labels are low-cardinality. */
    private static String normalize(String uri) {
        return uri == null ? "" : uri.replaceAll(UUID_REGEX, "{id}");
    }
}
