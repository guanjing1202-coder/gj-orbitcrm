package com.orbitcrm.openapi.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiKeyValidatorTest {
    private static final String TENANT_CODE = "demo-company";
    private static final String RAW_KEY = "orb_demo_secret";
    private static final String KEY_HASH = "hashed-key";

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void requireScopeRejectsUnknownSubscriptionStatus() {
        JdbcTemplate platformJdbcTemplate = platformJdbcTemplateWithSubscriptionStatus("SUSPENDED");
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OpenApiKeyValidator validator = new OpenApiKeyValidator(
                platformJdbcTemplate, tenantJdbcTemplateProvider, keyHasher);
        TenantContext.setTenantCode(TENANT_CODE);
        when(request.getHeader(OpenApiKeyValidator.OPENAPI_KEY_HEADER)).thenReturn(RAW_KEY);
        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(keyHasher.sha256(RAW_KEY)).thenReturn(KEY_HASH);
        activeOpenApiKey(tenantJdbcTemplate, 17L, "leads:write");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.requireScope(request, "leads:write"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(anyString(), anyLong());
    }

    @Test
    void requireScopeAllowsValidKeyAndUpdatesLastUsedTime() {
        JdbcTemplate platformJdbcTemplate = platformJdbcTemplateWithSubscriptionStatus("ACTIVE");
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        OpenApiKeyValidator validator = new OpenApiKeyValidator(
                platformJdbcTemplate, tenantJdbcTemplateProvider, keyHasher);
        TenantContext.setTenantCode(TENANT_CODE);
        when(request.getHeader(OpenApiKeyValidator.OPENAPI_KEY_HEADER)).thenReturn(RAW_KEY);
        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(keyHasher.sha256(RAW_KEY)).thenReturn(KEY_HASH);
        activeOpenApiKey(tenantJdbcTemplate, 17L, "leads:read, leads:write");

        validator.requireScope(request, "leads:write");

        verify(tenantJdbcTemplate).update(
                "UPDATE sys_openapi_key SET last_used_time = NOW() WHERE id = ?",
                17L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcTemplate platformJdbcTemplateWithSubscriptionStatus(String status) {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("status")).thenReturn(status);
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                });
        return platformJdbcTemplate;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void activeOpenApiKey(JdbcTemplate tenantJdbcTemplate, Long id, String scopes) {
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(id);
                    when(resultSet.getString("scopes")).thenReturn(scopes);
                    return mapper.mapRow(resultSet, 0);
                });
    }
}
