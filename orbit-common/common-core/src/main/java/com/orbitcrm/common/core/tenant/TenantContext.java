package com.orbitcrm.common.core.tenant;

public final class TenantContext {
    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<String>();

    private TenantContext() {
    }

    public static void setTenantCode(String tenantCode) {
        TENANT_CODE.set(tenantCode);
    }

    public static String getTenantCode() {
        return TENANT_CODE.get();
    }

    public static void clear() {
        TENANT_CODE.remove();
    }
}
