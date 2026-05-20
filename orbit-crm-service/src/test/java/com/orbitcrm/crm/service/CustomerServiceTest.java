package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.CustomerCreateRequest;
import com.orbitcrm.crm.api.CustomerResponse;
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

class CustomerServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createCustomerChecksPlanLimitAndReadsCreatedCustomerFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService planLimitService = mock(PlanLimitService.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        CustomerService service = new CustomerService(tenantJdbcTemplateProvider, planLimitService);
        CustomerCreateRequest request = new CustomerCreateRequest();
        request.setCustomerName("Orbit Labs");
        request.setCustomerType("ENTERPRISE");
        request.setPhone("18800003333");
        request.setEmail("hello@orbit.example");
        request.setAddress("Shanghai");
        request.setOwnerUserId(15L);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(66L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(66L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(66L);
                    when(resultSet.getString("customer_name")).thenReturn("Orbit Labs");
                    when(resultSet.getString("customer_type")).thenReturn("ENTERPRISE");
                    when(resultSet.getString("phone")).thenReturn("18800003333");
                    when(resultSet.getString("email")).thenReturn("hello@orbit.example");
                    when(resultSet.getString("address")).thenReturn("Shanghai");
                    when(resultSet.getObject("owner_user_id")).thenReturn(15L);
                    when(resultSet.getString("status")).thenReturn("ACTIVE");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 14, 40)));
                    return mapper.mapRow(resultSet, 0);
                });

        CustomerResponse response = service.createCustomer(request);

        assertNotNull(response);
        assertEquals(66L, response.getId());
        assertEquals("Orbit Labs", response.getCustomerName());
        assertEquals("ENTERPRISE", response.getCustomerType());
        assertEquals("18800003333", response.getPhone());
        assertEquals("hello@orbit.example", response.getEmail());
        assertEquals("Shanghai", response.getAddress());
        assertEquals(15L, response.getOwnerUserId());
        assertEquals("ACTIVE", response.getStatus());
        verify(planLimitService).assertCustomerLimitAvailable();
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_customer (customer_name, customer_type, phone, email, address, owner_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')"),
                eq("Orbit Labs"),
                eq("ENTERPRISE"),
                eq("18800003333"),
                eq("hello@orbit.example"),
                eq("Shanghai"),
                eq(15L));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
