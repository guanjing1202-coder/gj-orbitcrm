package com.orbitcrm.common.datasource;

import com.orbitcrm.common.core.tenant.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicTenantDataSource extends AbstractRoutingDataSource {
    public static final String PLATFORM = "platform";

    @Override
    protected Object determineCurrentLookupKey() {
        String tenantCode = TenantContext.getTenantCode();
        return tenantCode == null || tenantCode.trim().isEmpty() ? PLATFORM : tenantCode;
    }
}
