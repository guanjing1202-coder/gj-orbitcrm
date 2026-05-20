package com.orbitcrm.report.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.report.api.DashboardSummaryResponse;
import com.orbitcrm.report.api.DealFunnelStageResponse;
import com.orbitcrm.report.api.NameValueResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void summaryReadsDashboardMetricsFromCurrentTenantJdbcTemplate() {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        DashboardService service = new DashboardService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_lead WHERE status <> 'DELETED'",
                Integer.class)).thenReturn(12);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE status <> 'DELETED'",
                Integer.class)).thenReturn(8);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_deal WHERE status = 'OPEN'",
                Integer.class)).thenReturn(5);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_cent), 0) FROM crm_deal WHERE status = 'OPEN'",
                Long.class)).thenReturn(3500000L);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_task WHERE status = 'TODO' AND DATE(due_time) = CURDATE()",
                Integer.class)).thenReturn(3);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_task WHERE status = 'TODO' AND due_time < NOW()",
                Integer.class)).thenReturn(1);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(Arrays.asList(new NameValueResponse("NEW", 4L)))
                .thenReturn(Arrays.asList(new NameValueResponse("OPEN", 5L)))
                .thenReturn(Arrays.asList(new NameValueResponse("TODO", 3L)))
                .thenReturn(Arrays.asList(new NameValueResponse("Qualification", 1200000L)));

        DashboardSummaryResponse response = service.summary();

        assertNotNull(response);
        assertEquals(12, response.getLeadCount());
        assertEquals(8, response.getCustomerCount());
        assertEquals(5, response.getOpenDealCount());
        assertEquals(3500000L, response.getOpenDealAmountCent());
        assertEquals(3, response.getTodayTaskCount());
        assertEquals(1, response.getOverdueTaskCount());
        assertEquals("NEW", response.getLeadStatus().get(0).getName());
        assertEquals("OPEN", response.getDealStatus().get(0).getName());
        assertEquals("TODO", response.getTaskStatus().get(0).getName());
        assertEquals("Qualification", response.getDealFunnel().get(0).getName());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dealFunnelReturnsDetailedStageMetrics() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        DashboardService service = new DashboardService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(9L);
                    when(resultSet.getString("stage_name")).thenReturn("Proposal");
                    when(resultSet.getInt("win_probability")).thenReturn(60);
                    when(resultSet.getInt("sort_order")).thenReturn(3);
                    when(resultSet.getLong("deal_count")).thenReturn(2L);
                    when(resultSet.getLong("total_amount")).thenReturn(1800000L);
                    return Arrays.asList(mapper.mapRow(resultSet, 0));
                });

        List<DealFunnelStageResponse> response = service.dealFunnel();

        assertEquals(1, response.size());
        assertEquals(9L, response.get(0).getStageId());
        assertEquals("Proposal", response.get(0).getStageName());
        assertEquals(60, response.get(0).getWinProbability());
        assertEquals(3, response.get(0).getSortOrder());
        assertEquals(2L, response.get(0).getDealCount());
        assertEquals(1800000L, response.get(0).getTotalAmountCent());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verifyNoMoreInteractions(tenantJdbcTemplateProvider);
    }
}
