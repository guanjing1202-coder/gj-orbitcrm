package com.orbitcrm.common.datasource;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantJdbcTemplateProviderTest {
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createTenantJdbcTemplateUsesPooledHikariDataSource() {
        TenantJdbcTemplateProvider provider = new TenantJdbcTemplateProvider(
                mock(JdbcTemplate.class),
                new TenantJdbcTemplateFactory());
        TenantDatabaseProperties properties = new TenantDatabaseProperties();
        properties.setTenantCode("demo-company");
        properties.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo");
        properties.setUsername("demo_user");
        properties.setPassword("demo_password");

        JdbcTemplate jdbcTemplate = provider.createTenantJdbcTemplate(properties);
        DataSource dataSource = jdbcTemplate.getDataSource();

        HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, dataSource);
        assertEquals("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo", hikariDataSource.getJdbcUrl());
        assertEquals("demo_user", hikariDataSource.getUsername());
    }

    @Test
    void factoryDecodesBase64PasswordCipher() {
        TenantJdbcTemplateFactory factory = new TenantJdbcTemplateFactory();
        TenantDatabaseProperties properties = new TenantDatabaseProperties();
        properties.setTenantCode("demo-company");
        properties.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo");
        properties.setUsername("demo_user");
        properties.setPassword(Base64.getEncoder().encodeToString("plain_secret".getBytes(StandardCharsets.UTF_8)));

        JdbcTemplate jdbcTemplate = factory.createTenantJdbcTemplate(properties);
        HikariDataSource hikariDataSource = assertInstanceOf(HikariDataSource.class, jdbcTemplate.getDataSource());

        assertEquals("plain_secret", hikariDataSource.getPassword());
        assertEquals("orbit-tenant-demo-company", hikariDataSource.getPoolName());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void refreshTenantJdbcTemplateClosesEvictedDataSourceAndCreatesNewOne() throws Exception {
        JdbcTemplate platformJdbcTemplate = tenantPlatformJdbcTemplate("demo-company");
        TenantJdbcTemplateProvider provider = new TenantJdbcTemplateProvider(
                platformJdbcTemplate, new TenantJdbcTemplateFactory());
        TenantContext.setTenantCode("demo-company");

        JdbcTemplate firstJdbcTemplate = provider.currentTenantJdbcTemplate();
        HikariDataSource firstDataSource = assertInstanceOf(
                HikariDataSource.class, firstJdbcTemplate.getDataSource());

        provider.refreshTenantJdbcTemplate("demo-company");
        JdbcTemplate secondJdbcTemplate = provider.currentTenantJdbcTemplate();
        HikariDataSource secondDataSource = assertInstanceOf(
                HikariDataSource.class, secondJdbcTemplate.getDataSource());

        assertTrue(firstDataSource.isClosed());
        assertNotSame(firstJdbcTemplate, secondJdbcTemplate);
        assertNotSame(firstDataSource, secondDataSource);
    }

    @Test
    void destroyClosesCachedTenantDataSources() {
        JdbcTemplate platformJdbcTemplate = tenantPlatformJdbcTemplate("demo-company");
        TenantJdbcTemplateProvider provider = new TenantJdbcTemplateProvider(
                platformJdbcTemplate, new TenantJdbcTemplateFactory());
        TenantContext.setTenantCode("demo-company");
        JdbcTemplate jdbcTemplate = provider.currentTenantJdbcTemplate();
        HikariDataSource dataSource = assertInstanceOf(HikariDataSource.class, jdbcTemplate.getDataSource());

        provider.destroy();

        assertTrue(dataSource.isClosed());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcTemplate tenantPlatformJdbcTemplate(String tenantCode) {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("tenant_code")).thenReturn(tenantCode);
                    when(resultSet.getString("jdbc_url"))
                            .thenReturn("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo");
                    when(resultSet.getString("username")).thenReturn("demo_user");
                    when(resultSet.getString("password_cipher")).thenReturn("demo_password");
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                });
        return platformJdbcTemplate;
    }
}
