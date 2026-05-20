package com.orbitcrm.auth.service;

import com.orbitcrm.auth.api.LoginRequest;
import com.orbitcrm.auth.api.LoginResponse;
import com.orbitcrm.common.datasource.TenantDatabaseProperties;
import com.orbitcrm.common.datasource.TenantJdbcTemplateFactory;
import com.orbitcrm.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void loginCreatesTenantJdbcTemplateThroughFactory() throws Exception {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateFactory tenantJdbcTemplateFactory = mock(TenantJdbcTemplateFactory.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
                "01234567890123456789012345678901", 3600000);
        LoginService loginService = new LoginService(
                platformJdbcTemplate, tenantJdbcTemplateFactory, jwtTokenProvider, 3600000);

        when(tenantJdbcTemplateFactory.createTenantJdbcTemplate(any(TenantDatabaseProperties.class)))
                .thenReturn(tenantJdbcTemplate);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("tenant_id")).thenReturn(7L);
                    when(resultSet.getString("tenant_status")).thenReturn("ACTIVE");
                    when(resultSet.getString("jdbc_url")).thenReturn("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo");
                    when(resultSet.getString("username")).thenReturn("demo_user");
                    when(resultSet.getString("password_cipher")).thenReturn("demo_password");
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                })
                .thenReturn(Collections.singletonList("TRIAL"));
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(11L);
                    when(resultSet.getString("username")).thenReturn("admin");
                    when(resultSet.getString("password_hash"))
                            .thenReturn(new BCryptPasswordEncoder().encode("secret123"));
                    when(resultSet.getString("status")).thenReturn("ACTIVE");
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                });

        LoginRequest request = new LoginRequest();
        request.setTenantCode(" Demo-Company ");
        request.setUsername("admin");
        request.setPassword("secret123");

        LoginResponse response = loginService.login(request, "127.0.0.1", "JUnit");

        ArgumentCaptor<TenantDatabaseProperties> propertiesCaptor =
                ArgumentCaptor.forClass(TenantDatabaseProperties.class);
        verify(tenantJdbcTemplateFactory).createTenantJdbcTemplate(propertiesCaptor.capture());
        TenantDatabaseProperties properties = propertiesCaptor.getValue();
        assertEquals("demo-company", properties.getTenantCode());
        assertEquals("jdbc:mysql://127.0.0.1:3306/orbit_tenant_demo", properties.getJdbcUrl());
        assertEquals("demo_user", properties.getUsername());
        assertEquals("demo_password", properties.getPassword());
        assertEquals("demo-company", response.getTenantCode());
        assertEquals(11L, response.getUserId());
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO sys_login_log (user_id, username, login_result, ip, user_agent) VALUES (?, ?, ?, ?, ?)"),
                eq(11L),
                eq("admin"),
                eq("SUCCESS"),
                eq("127.0.0.1"),
                eq("JUnit"));
    }
}
