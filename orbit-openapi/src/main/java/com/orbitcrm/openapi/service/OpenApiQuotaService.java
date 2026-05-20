package com.orbitcrm.openapi.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OpenApiQuotaService {
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public OpenApiQuotaService(JdbcTemplate platformJdbcTemplate,
                               TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public void assertLeadLimitAvailable() {
        int limit = currentLimit("max_leads");
        if (limit < 0) {
            return;
        }
        Integer used = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM crm_lead WHERE status <> 'DELETED'",
                Integer.class);
        if (used != null && used >= limit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "lead limit exceeded");
        }
    }

    private int currentLimit(String featureKey) {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        List<String> values = platformJdbcTemplate.query(
                "SELECT f.feature_value FROM platform_tenant t " +
                        "JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "JOIN platform_plan_feature f ON s.plan_id = f.plan_id " +
                        "WHERE t.tenant_code = ? AND f.feature_key = ? " +
                        "ORDER BY s.id DESC LIMIT 1",
                new Object[]{tenantCode, featureKey},
                (rs, rowNum) -> rs.getString("feature_value"));
        if (values.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(values.get(0));
    }
}
