package com.orbitcrm.system.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.system.api.OperationLogResponse;
import com.orbitcrm.system.api.OperationLogStatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listLogsAppliesAuditFiltersAndSafeLimit() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OperationLogService service = new OperationLogService(tenantJdbcTemplateProvider);
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 20, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 21, 23, 59);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(101L);
                    when(resultSet.getObject("user_id")).thenReturn(18L);
                    when(resultSet.getString("action")).thenReturn("CUSTOMER_UPDATE");
                    when(resultSet.getString("target_type")).thenReturn("crm_customer");
                    when(resultSet.getString("target_id")).thenReturn("66");
                    when(resultSet.getString("detail_json")).thenReturn("{\"method\":\"CustomerService.updateCustomer\"}");
                    when(resultSet.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(startTime.plusHours(9)));
                    return Arrays.asList(mapper.mapRow(resultSet, 0));
                });

        List<OperationLogResponse> response = service.listLogs(
                "CUSTOMER_UPDATE",
                18L,
                "crm_customer",
                "66",
                startTime,
                endTime,
                999);

        assertEquals(1, response.size());
        assertEquals(101L, response.get(0).getId());
        assertEquals(18L, response.get(0).getUserId());
        assertEquals("CUSTOMER_UPDATE", response.get(0).getAction());
        assertEquals("crm_customer", response.get(0).getTargetType());
        assertEquals("66", response.get(0).getTargetId());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).query(
                eq("SELECT id, user_id, action, target_type, target_id, detail_json, create_time " +
                        "FROM sys_operation_log WHERE 1 = 1 AND action = ? AND user_id = ? " +
                        "AND target_type = ? AND target_id = ? AND create_time >= ? AND create_time <= ? " +
                        "ORDER BY id DESC LIMIT 500"),
                any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void actionStatsReturnsGroupedAuditMetrics() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        OperationLogService service = new OperationLogService(tenantJdbcTemplateProvider);
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 21, 0, 0);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("action")).thenReturn("LEAD_CREATE");
                    when(resultSet.getLong("total")).thenReturn(7L);
                    when(resultSet.getTimestamp("latest_time")).thenReturn(Timestamp.valueOf(startTime.plusHours(11)));
                    return Arrays.asList(mapper.mapRow(resultSet, 0));
                });

        List<OperationLogStatResponse> response = service.actionStats(startTime, null, 20);

        assertEquals(1, response.size());
        assertEquals("LEAD_CREATE", response.get(0).getAction());
        assertEquals(7L, response.get(0).getTotal());
        assertEquals(startTime.plusHours(11), response.get(0).getLatestTime());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).query(
                eq("SELECT action, COUNT(1) AS total, MAX(create_time) AS latest_time " +
                        "FROM sys_operation_log WHERE 1 = 1 AND create_time >= ? " +
                        "GROUP BY action ORDER BY total DESC, action LIMIT 20"),
                any(RowMapper.class),
                any(Object[].class));
    }
}
