package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
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

class DealServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createDealReadsCreatedDealFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        DealService service = new DealService(tenantJdbcTemplateProvider);
        DealCreateRequest request = new DealCreateRequest();
        request.setDealName("Annual Expansion");
        request.setCustomerId(66L);
        request.setPipelineId(3L);
        request.setStageId(8L);
        request.setAmountCent(1234500L);
        request.setExpectedCloseDate(LocalDate.of(2026, 6, 30));
        request.setOwnerUserId(15L);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline_stage WHERE pipeline_id = ? AND id = ?",
                Integer.class,
                3L,
                8L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(88L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(88L);
                    when(resultSet.getString("deal_name")).thenReturn("Annual Expansion");
                    when(resultSet.getObject("customer_id")).thenReturn(66L);
                    when(resultSet.getLong("pipeline_id")).thenReturn(3L);
                    when(resultSet.getLong("stage_id")).thenReturn(8L);
                    when(resultSet.getLong("amount_cent")).thenReturn(1234500L);
                    when(resultSet.getDate("expected_close_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 6, 30)));
                    when(resultSet.getObject("owner_user_id")).thenReturn(15L);
                    when(resultSet.getString("status")).thenReturn("OPEN");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 14, 50)));
                    return mapper.mapRow(resultSet, 0);
                });

        DealResponse response = service.createDeal(request);

        assertNotNull(response);
        assertEquals(88L, response.getId());
        assertEquals("Annual Expansion", response.getDealName());
        assertEquals(66L, response.getCustomerId());
        assertEquals(3L, response.getPipelineId());
        assertEquals(8L, response.getStageId());
        assertEquals(1234500L, response.getAmountCent());
        assertEquals(LocalDate.of(2026, 6, 30), response.getExpectedCloseDate());
        assertEquals(15L, response.getOwnerUserId());
        assertEquals("OPEN", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
