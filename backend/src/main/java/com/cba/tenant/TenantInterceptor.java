package com.cba.tenant;

import com.cba.common.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Populates TenantContext for every inbound request.
 *
 * Resolution order:
 *   1. X-Tenant-ID request header (tenant code, e.g. "KE", "GH", "DEFAULT")
 *   2. Falls back to "DEFAULT" if header is absent
 *
 * TenantContext is always cleared after the request completes (afterCompletion)
 * to prevent ThreadLocal leaks in thread-pool environments.
 */
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String tenantCode = request.getHeader(TENANT_HEADER);
        if (tenantCode == null || tenantCode.isBlank()) {
            tenantCode = TenantService.DEFAULT_TENANT_CODE;
        }
        TenantContext.setTenant(tenantCode);
        log.debug("Tenant context set: {}", tenantCode);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
