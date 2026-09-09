package com.engine.order.infrastructure.multitenancy;

public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "default-tenant";
    private static final ThreadLocal<String> CURRENT_TENANT = ThreadLocal.withInitial(() -> DEFAULT_TENANT_ID);

    private TenantContext() {
    }

    public static String getTenantId() {
        String tenant = CURRENT_TENANT.get();
        return tenant != null && !tenant.isBlank() ? tenant : DEFAULT_TENANT_ID;
    }

    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.trim());
        } else {
            CURRENT_TENANT.set(DEFAULT_TENANT_ID);
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
