package com.cba.common.tenant;

/**
 * ThreadLocal holder for the current tenant.
 * Nullable in v1 (single-tenant). Will be enforced in v2 via a Hibernate filter.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
