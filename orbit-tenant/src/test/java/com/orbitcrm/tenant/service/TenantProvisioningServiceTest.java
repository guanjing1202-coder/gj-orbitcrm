package com.orbitcrm.tenant.service;

import com.orbitcrm.common.datasource.TenantDatabaseProperties;
import com.orbitcrm.common.datasource.TenantJdbcTemplateFactory;
import com.orbitcrm.tenant.api.TenantRegisterRequest;
import com.orbitcrm.tenant.api.TenantRegisterResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantProvisioningServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void registerInitializesTenantDatabaseThroughFactory() throws Exception {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateFactory tenantJdbcTemplateFactory = mock(TenantJdbcTemplateFactory.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TenantProvisioningService provisioningService = new TenantProvisioningService(
                platformJdbcTemplate,
                tenantJdbcTemplateFactory,
                "db.internal",
                3307,
                "tenant_root",
                "root-secret",
                14);

        when(tenantJdbcTemplateFactory.createTenantJdbcTemplate(any(TenantDatabaseProperties.class)))
                .thenReturn(tenantJdbcTemplate);
        when(platformJdbcTemplate.queryForObject(
                startsWith("SELECT COUNT(1) FROM platform_tenant"),
                eq(Integer.class),
                eq("demo-company"))).thenReturn(0);
        when(platformJdbcTemplate.queryForObject(
                startsWith("SELECT id FROM platform_tenant WHERE tenant_code"),
                eq(Long.class),
                eq("demo-company"))).thenReturn(7L);
        when(platformJdbcTemplate.queryForObject(
                startsWith("SELECT id FROM platform_subscription"),
                eq(Long.class),
                eq(7L))).thenReturn(9L);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(5L);
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                });
        when(tenantJdbcTemplate.queryForObject(
                startsWith("SELECT id FROM sys_role"),
                eq(Long.class))).thenReturn(2L);
        when(tenantJdbcTemplate.queryForObject(
                startsWith("SELECT id FROM sys_user"),
                eq(Long.class),
                eq("admin"))).thenReturn(3L);

        TenantRegisterRequest request = new TenantRegisterRequest();
        request.setTenantCode(" Demo-Company ");
        request.setTenantName("Demo Company");
        request.setAdminUsername("admin");
        request.setAdminEmail("admin@example.com");
        request.setAdminPassword("secret123");
        request.setPlanCode("starter");

        TenantRegisterResponse response = provisioningService.register(request);

        ArgumentCaptor<TenantDatabaseProperties> propertiesCaptor =
                ArgumentCaptor.forClass(TenantDatabaseProperties.class);
        verify(tenantJdbcTemplateFactory).createTenantJdbcTemplate(propertiesCaptor.capture());
        TenantDatabaseProperties properties = propertiesCaptor.getValue();
        assertEquals("demo-company", properties.getTenantCode());
        assertEquals("jdbc:mysql://db.internal:3307/orbit_tenant_demo_company" +
                "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                properties.getJdbcUrl());
        assertEquals("tenant_root", properties.getUsername());
        assertEquals(Base64.getEncoder().encodeToString("root-secret".getBytes(StandardCharsets.UTF_8)),
                properties.getPassword());
        assertEquals("demo-company", response.getTenantCode());
        assertEquals("orbit_tenant_demo_company", response.getDatabaseName());
        assertEquals(7L, response.getTenantId());
        assertEquals(9L, response.getSubscriptionId());
        verify(platformJdbcTemplate).execute(
                "CREATE DATABASE IF NOT EXISTS `orbit_tenant_demo_company` " +
                        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }
}
