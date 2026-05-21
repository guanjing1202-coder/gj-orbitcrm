package com.orbitcrm.openapi.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.openapi.api.OpenApiKeyCreateRequest;
import com.orbitcrm.openapi.api.OpenApiKeyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenApiKeyServiceTest {
    @AfterEach
    void clearUser() {
        CurrentUserContext.clear();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createKeyReturnsSecretOnceAndReadsFromSameTenantJdbcTemplate() throws Exception {
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        OpenApiKeyService service = new OpenApiKeyService(tenantJdbcTemplateProvider, keyHasher);
        OpenApiKeyCreateRequest request = new OpenApiKeyCreateRequest();
        request.setKeyName("Marketing form");

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(keyHasher.sha256(anyString())).thenReturn("hashed-key");
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(88L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> mapKey(invocation.getArgument(1), "ACTIVE", null));

        OpenApiKeyResponse response = service.createKey(request);

        assertEquals(88L, response.getId());
        assertEquals("Marketing form", response.getKeyName());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("crm:lead:write", response.getScopes().get(0));
        assertNotNull(response.getSecretKey());
        assertTrue(response.getSecretKey().startsWith("orb_"));
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO sys_openapi_key (key_name, key_prefix, key_hash, scopes, creator_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE')"),
                eq("Marketing form"),
                anyString(),
                eq("hashed-key"),
                eq("crm:lead:write"),
                eq(12L));
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enableKeyActivatesDisabledKey() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        OpenApiKeyService service = new OpenApiKeyService(tenantJdbcTemplateProvider, keyHasher);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'ACTIVE' WHERE id = ? AND status = 'DISABLED'",
                88L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> mapKey(invocation.getArgument(1), "ACTIVE", null));

        OpenApiKeyResponse response = service.enableKey(88L);

        assertEquals("ACTIVE", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void disableKeyKeepsAlreadyDisabledKeyReadable() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        OpenApiKeyService service = new OpenApiKeyService(tenantJdbcTemplateProvider, keyHasher);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'DISABLED' WHERE id = ? AND status = 'ACTIVE'",
                88L)).thenReturn(0);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_openapi_key WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                88L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> mapKey(invocation.getArgument(1), "DISABLED", null));

        OpenApiKeyResponse response = service.disableKey(88L);

        assertEquals("DISABLED", response.getStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rotateKeyReturnsNewSecretWithoutChangingLifecycleStatus() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        OpenApiKeyService service = new OpenApiKeyService(tenantJdbcTemplateProvider, keyHasher);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(keyHasher.sha256(anyString())).thenReturn("rotated-hash");
        when(tenantJdbcTemplate.update(
                eq("UPDATE sys_openapi_key SET key_prefix = ?, key_hash = ?, last_used_time = NULL " +
                        "WHERE id = ? AND status <> 'DELETED'"),
                anyString(),
                eq("rotated-hash"),
                eq(88L))).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> mapKey(invocation.getArgument(1), "DISABLED", null));

        OpenApiKeyResponse response = service.rotateKey(88L);

        assertEquals("DISABLED", response.getStatus());
        assertNotNull(response.getSecretKey());
        assertTrue(response.getSecretKey().startsWith("orb_"));
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deleteKeySoftDeletesKeyAndReturnsDeletedStatus() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiKeyHasher keyHasher = mock(OpenApiKeyHasher.class);
        OpenApiKeyService service = new OpenApiKeyService(tenantJdbcTemplateProvider, keyHasher);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'DELETED' WHERE id = ? AND status <> 'DELETED'",
                88L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> mapKey(invocation.getArgument(1), "DELETED", null));

        OpenApiKeyResponse response = service.deleteKey(88L);

        assertEquals("DELETED", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    private OpenApiKeyResponse mapKey(RowMapper<OpenApiKeyResponse> mapper,
                                      String status,
                                      LocalDateTime lastUsedTime) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(88L);
        when(resultSet.getString("key_name")).thenReturn("Marketing form");
        when(resultSet.getString("key_prefix")).thenReturn("orb_preview");
        when(resultSet.getString("scopes")).thenReturn("crm:lead:write");
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("last_used_time"))
                .thenReturn(lastUsedTime == null ? null : Timestamp.valueOf(lastUsedTime));
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }
}
