package com.orbitcrm.openapi.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@Service
public class OpenApiKeyValidator {
    public static final String OPENAPI_KEY_HEADER = "X-OpenAPI-Key";
    private static final List<String> OPENAPI_ALLOWED_SUBSCRIPTION_STATUSES =
            Arrays.asList("TRIAL", "ACTIVE", "PAST_DUE");

    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final OpenApiKeyHasher keyHasher;

    public OpenApiKeyValidator(JdbcTemplate platformJdbcTemplate,
                               TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                               OpenApiKeyHasher keyHasher) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.keyHasher = keyHasher;
    }

    public void requireScope(HttpServletRequest request, String requiredScope) {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        String rawKey = request.getHeader(OPENAPI_KEY_HEADER);
        if (!StringUtils.hasText(rawKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "openapi key is required");
        }

        assertSubscriptionAllowsOpenApi(tenantCode);
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        OpenApiKeyRecord record = findActiveKey(jdbcTemplate, keyHasher.sha256(rawKey));
        if (!hasScope(record.getScopes(), requiredScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "openapi scope denied");
        }
        jdbcTemplate.update("UPDATE sys_openapi_key SET last_used_time = NOW() WHERE id = ?", record.getId());
    }

    private void assertSubscriptionAllowsOpenApi(String tenantCode) {
        List<String> statuses = platformJdbcTemplate.query(
                "SELECT s.status FROM platform_tenant t JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "WHERE t.tenant_code = ? AND t.status = 'ACTIVE' ORDER BY s.id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("status"),
                tenantCode
            );
        if (statuses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "subscription is not available");
        }
        String status = statuses.get(0);
        if (!OPENAPI_ALLOWED_SUBSCRIPTION_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "subscription does not allow openapi access");
        }
    }

    private OpenApiKeyRecord findActiveKey(JdbcTemplate jdbcTemplate, String keyHash) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, scopes FROM sys_openapi_key WHERE key_hash = ? AND status = 'ACTIVE'",
                    (rs, rowNum) -> new OpenApiKeyRecord(rs.getLong("id"), rs.getString("scopes")),
                    keyHash
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "openapi key is invalid", ex);
        }
    }

    private boolean hasScope(String scopes, String requiredScope) {
        if (!StringUtils.hasText(scopes)) {
            return false;
        }
        List<String> parts = Arrays.asList(scopes.split(","));
        for (String part : parts) {
            String scope = part.trim();
            if ("*".equals(scope) || requiredScope.equals(scope)) {
                return true;
            }
        }
        return false;
    }

    private static class OpenApiKeyRecord {
        private final Long id;
        private final String scopes;

        OpenApiKeyRecord(Long id, String scopes) {
            this.id = id;
            this.scopes = scopes;
        }

        Long getId() {
            return id;
        }

        String getScopes() {
            return scopes;
        }
    }
}
