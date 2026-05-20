package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.api.LeadCreateRequest;
import com.orbitcrm.crm.api.LeadResponse;
import com.orbitcrm.crm.api.LeadUpdateRequest;
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

class LeadServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createLeadChecksPlanLimitAndReadsCreatedLeadFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        LeadService service = new LeadService(tenantJdbcTemplateProvider, planLimitService);
        LeadCreateRequest request = new LeadCreateRequest();
        request.setLeadName("Grace Hopper");
        request.setCompanyName("Compiler Labs");
        request.setPhone("18800002222");
        request.setEmail("grace@example.com");
        request.setOwnerUserId(12L);
        request.setSource("WEBSITE");

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(55L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(55L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(55L);
                    when(resultSet.getString("lead_name")).thenReturn("Grace Hopper");
                    when(resultSet.getString("company_name")).thenReturn("Compiler Labs");
                    when(resultSet.getString("phone")).thenReturn("18800002222");
                    when(resultSet.getString("email")).thenReturn("grace@example.com");
                    when(resultSet.getString("status")).thenReturn("NEW");
                    when(resultSet.getObject("owner_user_id")).thenReturn(12L);
                    when(resultSet.getString("source")).thenReturn("WEBSITE");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 13, 30)));
                    return mapper.mapRow(resultSet, 0);
                });

        LeadResponse response = service.createLead(request);

        assertNotNull(response);
        assertEquals(55L, response.getId());
        assertEquals("Grace Hopper", response.getLeadName());
        assertEquals("Compiler Labs", response.getCompanyName());
        assertEquals("18800002222", response.getPhone());
        assertEquals("grace@example.com", response.getEmail());
        assertEquals("NEW", response.getStatus());
        assertEquals(12L, response.getOwnerUserId());
        assertEquals("WEBSITE", response.getSource());
        verify(planLimitService).assertLeadLimitAvailable();
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_lead (lead_name, company_name, phone, email, status, owner_user_id, source) " +
                        "VALUES (?, ?, ?, ?, 'NEW', ?, ?)"),
                eq("Grace Hopper"),
                eq("Compiler Labs"),
                eq("18800002222"),
                eq("grace@example.com"),
                eq(12L),
                eq("WEBSITE"));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateLeadReadsUpdatedLeadFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        LeadService service = new LeadService(tenantJdbcTemplateProvider, planLimitService);
        LeadUpdateRequest request = new LeadUpdateRequest();
        request.setLeadName("Grace Hopper Updated");
        request.setPhone("18800006666");
        request.setOwnerUserId(18L);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(55L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(55L);
                    when(resultSet.getString("lead_name")).thenReturn("Grace Hopper Updated");
                    when(resultSet.getString("company_name")).thenReturn("Compiler Labs");
                    when(resultSet.getString("phone")).thenReturn("18800006666");
                    when(resultSet.getString("email")).thenReturn("grace@example.com");
                    when(resultSet.getString("status")).thenReturn("NEW");
                    when(resultSet.getObject("owner_user_id")).thenReturn(18L);
                    when(resultSet.getString("source")).thenReturn("WEBSITE");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 13, 30)));
                    return mapper.mapRow(resultSet, 0);
                });

        LeadResponse response = service.updateLead(55L, request);

        assertNotNull(response);
        assertEquals("Grace Hopper Updated", response.getLeadName());
        assertEquals("18800006666", response.getPhone());
        assertEquals(18L, response.getOwnerUserId());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("UPDATE crm_lead SET lead_name = ?, phone = ?, owner_user_id = ? WHERE id = ? AND status <> 'DELETED'"),
                eq("Grace Hopper Updated"),
                eq("18800006666"),
                eq(18L),
                eq(55L));
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void restoreLeadChecksPlanLimitAndReadsRestoredLeadFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        LeadService service = new LeadService(tenantJdbcTemplateProvider, planLimitService);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE crm_lead SET status = 'NEW' WHERE id = ? AND status = 'DELETED'",
                55L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(55L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(55L);
                    when(resultSet.getString("lead_name")).thenReturn("Grace Hopper");
                    when(resultSet.getString("company_name")).thenReturn("Compiler Labs");
                    when(resultSet.getString("phone")).thenReturn("18800002222");
                    when(resultSet.getString("email")).thenReturn("grace@example.com");
                    when(resultSet.getString("status")).thenReturn("NEW");
                    when(resultSet.getObject("owner_user_id")).thenReturn(12L);
                    when(resultSet.getString("source")).thenReturn("WEBSITE");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 13, 30)));
                    return mapper.mapRow(resultSet, 0);
                });

        LeadResponse response = service.restoreLead(55L);

        assertNotNull(response);
        assertEquals("NEW", response.getStatus());
        verify(planLimitService).assertLeadLimitAvailable();
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                "UPDATE crm_lead SET status = 'NEW' WHERE id = ? AND status = 'DELETED'",
                55L);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void convertToCustomerReadsLeadAndCreatedCustomerFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        LeadService service = new LeadService(tenantJdbcTemplateProvider, planLimitService);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(77L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(77L);
                    when(resultSet.getString("lead_name")).thenReturn("Katherine Johnson");
                    when(resultSet.getString("company_name")).thenReturn("Orbital Math");
                    when(resultSet.getString("phone")).thenReturn("18800004444");
                    when(resultSet.getString("email")).thenReturn("katherine@example.com");
                    when(resultSet.getString("status")).thenReturn("QUALIFIED");
                    when(resultSet.getObject("owner_user_id")).thenReturn(16L);
                    when(resultSet.getString("source")).thenReturn("REFERRAL");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 15, 35)));
                    return mapper.mapRow(resultSet, 0);
                });
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(78L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(78L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(78L);
                    when(resultSet.getString("customer_name")).thenReturn("Orbital Math");
                    when(resultSet.getString("customer_type")).thenReturn(null);
                    when(resultSet.getString("phone")).thenReturn("18800004444");
                    when(resultSet.getString("email")).thenReturn("katherine@example.com");
                    when(resultSet.getString("address")).thenReturn(null);
                    when(resultSet.getObject("owner_user_id")).thenReturn(16L);
                    when(resultSet.getString("status")).thenReturn("ACTIVE");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 15, 36)));
                    return mapper.mapRow(resultSet, 0);
                });

        CustomerResponse response = service.convertToCustomer(77L);

        assertNotNull(response);
        assertEquals(78L, response.getId());
        assertEquals("Orbital Math", response.getCustomerName());
        assertEquals("18800004444", response.getPhone());
        assertEquals("katherine@example.com", response.getEmail());
        assertEquals(16L, response.getOwnerUserId());
        assertEquals("ACTIVE", response.getStatus());
        verify(planLimitService).assertCustomerLimitAvailable();
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_customer (customer_name, phone, email, owner_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')"),
                eq("Orbital Math"),
                eq("18800004444"),
                eq("katherine@example.com"),
                eq(16L));
        verify(tenantJdbcTemplate).update("UPDATE crm_lead SET status = 'CONVERTED' WHERE id = ?", 77L);
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
