package com.orbitcrm.openapi.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.openapi.api.OpenApiLeadCreateRequest;
import com.orbitcrm.openapi.api.OpenApiLeadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpenApiLeadServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createLeadChecksQuotaAndReadsCreatedLeadFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        OpenApiQuotaService openApiQuotaService = mock(OpenApiQuotaService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        OpenApiLeadService service = new OpenApiLeadService(tenantJdbcTemplateProvider, openApiQuotaService);
        OpenApiLeadCreateRequest request = new OpenApiLeadCreateRequest();
        request.setLeadName("Ada Lovelace");
        request.setCompanyName("Analytical Engines");
        request.setPhone("18800001111");
        request.setEmail("ada@example.com");
        request.setOwnerUserId(9L);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(42L);
                    when(resultSet.getString("lead_name")).thenReturn("Ada Lovelace");
                    when(resultSet.getString("company_name")).thenReturn("Analytical Engines");
                    when(resultSet.getString("phone")).thenReturn("18800001111");
                    when(resultSet.getString("email")).thenReturn("ada@example.com");
                    when(resultSet.getString("status")).thenReturn("NEW");
                    when(resultSet.getObject("owner_user_id")).thenReturn(9L);
                    when(resultSet.getString("source")).thenReturn("OPENAPI");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 13, 10)));
                    return mapper.mapRow(resultSet, 0);
                });

        OpenApiLeadResponse response = service.createLead(request);

        assertNotNull(response);
        assertEquals(42L, response.getId());
        assertEquals("Ada Lovelace", response.getLeadName());
        assertEquals("Analytical Engines", response.getCompanyName());
        assertEquals("18800001111", response.getPhone());
        assertEquals("ada@example.com", response.getEmail());
        assertEquals("NEW", response.getStatus());
        assertEquals(9L, response.getOwnerUserId());
        assertEquals("OPENAPI", response.getSource());
        verify(openApiQuotaService).assertLeadLimitAvailable();
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_lead (lead_name, company_name, phone, email, status, owner_user_id, source) " +
                        "VALUES (?, ?, ?, ?, 'NEW', ?, ?)"),
                eq("Ada Lovelace"),
                eq("Analytical Engines"),
                eq("18800001111"),
                eq("ada@example.com"),
                eq(9L),
                eq("OPENAPI"));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
