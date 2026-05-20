package com.orbitcrm.crm.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanLimitServiceTest {
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void assertLeadLimitAvailableSkipsTenantCountForUnlimitedPlan() {
        JdbcTemplate platformJdbcTemplate = platformJdbcTemplateWithFeatureValue("-1");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService service = new PlanLimitService(platformJdbcTemplate, tenantJdbcTemplateProvider);
        TenantContext.setTenantCode("demo-company");

        service.assertLeadLimitAvailable();

        verify(tenantJdbcTemplateProvider, never()).currentTenantJdbcTemplate();
    }

    @Test
    void assertLeadLimitAvailableRejectsWhenLeadCountReachesPlanLimit() {
        JdbcTemplate platformJdbcTemplate = platformJdbcTemplateWithFeatureValue("3");
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService service = new PlanLimitService(platformJdbcTemplate, tenantJdbcTemplateProvider);
        TenantContext.setTenantCode("demo-company");
        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_lead WHERE status <> 'DELETED'",
                Integer.class)).thenReturn(3);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::assertLeadLimitAvailable);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void assertCustomerLimitAvailableRejectsWhenCustomerCountReachesPlanLimit() {
        JdbcTemplate platformJdbcTemplate = platformJdbcTemplateWithFeatureValue("2");
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        PlanLimitService service = new PlanLimitService(platformJdbcTemplate, tenantJdbcTemplateProvider);
        TenantContext.setTenantCode("demo-company");
        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE status <> 'DELETED'",
                Integer.class)).thenReturn(2);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::assertCustomerLimitAvailable);

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcTemplate platformJdbcTemplateWithFeatureValue(String featureValue) {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("feature_value")).thenReturn(featureValue);
                    return Collections.singletonList(mapper.mapRow(resultSet, 0));
                });
        return platformJdbcTemplate;
    }
}
