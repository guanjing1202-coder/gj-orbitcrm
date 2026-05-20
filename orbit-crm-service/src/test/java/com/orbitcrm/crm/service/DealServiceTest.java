package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealResponse;
import com.orbitcrm.crm.api.DealUpdateRequest;
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateDealReadsUpdatedDealFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        DealService service = new DealService(tenantJdbcTemplateProvider);
        DealUpdateRequest request = new DealUpdateRequest();
        request.setDealName("Annual Expansion Updated");
        request.setPipelineId(4L);
        request.setStageId(9L);
        request.setAmountCent(2234500L);
        request.setOwnerUserId(16L);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline_stage WHERE pipeline_id = ? AND id = ?",
                Integer.class,
                4L,
                9L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(88L);
                    when(resultSet.getString("deal_name")).thenReturn("Annual Expansion Updated");
                    when(resultSet.getObject("customer_id")).thenReturn(66L);
                    when(resultSet.getLong("pipeline_id")).thenReturn(4L);
                    when(resultSet.getLong("stage_id")).thenReturn(9L);
                    when(resultSet.getLong("amount_cent")).thenReturn(2234500L);
                    when(resultSet.getDate("expected_close_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 7, 31)));
                    when(resultSet.getObject("owner_user_id")).thenReturn(16L);
                    when(resultSet.getString("status")).thenReturn("OPEN");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 16, 20)));
                    return mapper.mapRow(resultSet, 0);
                });

        DealResponse response = service.updateDeal(88L, request);

        assertNotNull(response);
        assertEquals("Annual Expansion Updated", response.getDealName());
        assertEquals(4L, response.getPipelineId());
        assertEquals(9L, response.getStageId());
        assertEquals(2234500L, response.getAmountCent());
        assertEquals(16L, response.getOwnerUserId());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("UPDATE crm_deal SET deal_name = ?, pipeline_id = ?, stage_id = ?, amount_cent = ?, owner_user_id = ? WHERE id = ? AND status <> 'DELETED'"),
                eq("Annual Expansion Updated"),
                eq(4L),
                eq(9L),
                eq(2234500L),
                eq(16L),
                eq(88L));
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void winDealUpdatesStatusAndReadsDealFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        DealService service = new DealService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE crm_deal SET status = ? WHERE id = ? AND status <> 'DELETED'",
                "WON",
                88L)).thenReturn(1);
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
                    when(resultSet.getString("status")).thenReturn("WON");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 14, 50)));
                    return mapper.mapRow(resultSet, 0);
                });

        DealResponse response = service.winDeal(88L);

        assertNotNull(response);
        assertEquals("WON", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                "UPDATE crm_deal SET status = ? WHERE id = ? AND status <> 'DELETED'",
                "WON",
                88L);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
