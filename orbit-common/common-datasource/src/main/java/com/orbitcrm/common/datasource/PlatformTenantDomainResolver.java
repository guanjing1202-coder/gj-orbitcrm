package com.orbitcrm.common.datasource;

import com.orbitcrm.common.core.tenant.TenantDomainResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class PlatformTenantDomainResolver implements TenantDomainResolver {
    private final JdbcTemplate platformJdbcTemplate;

    public PlatformTenantDomainResolver(JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    @Override
    public String resolveTenantCode(String host) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        String normalizedHost = normalizeDomain(host);
        List<String> tenantCodes = platformJdbcTemplate.query(
                "SELECT t.tenant_code FROM platform_tenant_domain d " +
                        "JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE d.domain = ? AND d.verify_status = 'VERIFIED' " +
                        "AND d.status = 'ACTIVE' AND t.status = 'ACTIVE' " +
                        "ORDER BY d.id DESC LIMIT 1",
                new Object[]{normalizedHost},
                (rs, rowNum) -> rs.getString("tenant_code"));
        return tenantCodes.isEmpty() ? null : tenantCodes.get(0);
    }

    private String normalizeDomain(String domain) {
        String normalized = domain.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.endsWith(".")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
