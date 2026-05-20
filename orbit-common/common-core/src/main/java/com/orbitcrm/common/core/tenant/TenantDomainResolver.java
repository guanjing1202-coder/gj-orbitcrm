package com.orbitcrm.common.core.tenant;

public interface TenantDomainResolver {
    String resolveTenantCode(String host);
}
