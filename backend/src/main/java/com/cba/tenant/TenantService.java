package com.cba.tenant;

import com.cba.common.exception.CbaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    /** Default tenant code used when no X-Tenant-ID header is provided */
    public static final String DEFAULT_TENANT_CODE = "DEFAULT";

    @Transactional(readOnly = true)
    @Cacheable("tenants")
    public Tenant getTenantByCode(String code) {
        return tenantRepository.findByCodeAndActiveTrue(code)
            .orElseThrow(() -> CbaException.notFound("Tenant", code));
    }

    @Transactional(readOnly = true)
    public Tenant getTenantById(UUID id) {
        return tenantRepository.findById(id)
            .orElseThrow(() -> CbaException.notFound("Tenant", id));
    }

    @Transactional(readOnly = true)
    public Tenant getDefaultTenant() {
        return getTenantByCode(DEFAULT_TENANT_CODE);
    }

    /**
     * Returns the base currency for the given tenant code.
     * Falls back to USD if tenant not found (defensive — should not happen in prod).
     */
    public String getBaseCurrency(String tenantCode) {
        try {
            return getTenantByCode(tenantCode).getCurrencyCode();
        } catch (CbaException e) {
            log.warn("Tenant not found for code '{}', defaulting to USD", tenantCode);
            return "USD";
        }
    }
}
