package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.PipelineCreateRequest;
import com.orbitcrm.crm.api.PipelineResponse;
import com.orbitcrm.crm.api.PipelineStageCreateRequest;
import com.orbitcrm.crm.api.PipelineStageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;

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

class PipelineServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createDefaultPipelineClearsDefaultAndReadsPipelineFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        PipelineService service = new PipelineService(tenantJdbcTemplateProvider);
        PipelineCreateRequest request = new PipelineCreateRequest();
        request.setPipelineName("Enterprise Sales");
        request.setDefaultPipeline(true);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(9L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(9L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(9L);
                    when(resultSet.getString("pipeline_name")).thenReturn("Enterprise Sales");
                    when(resultSet.getInt("is_default")).thenReturn(1);
                    return mapper.mapRow(resultSet, 0);
                });
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(9L)))
                .thenReturn(new ArrayList<PipelineStageResponse>());

        PipelineResponse response = service.createPipeline(request);

        assertNotNull(response);
        assertEquals(9L, response.getId());
        assertEquals("Enterprise Sales", response.getPipelineName());
        assertEquals(Boolean.TRUE, response.getDefaultPipeline());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update("UPDATE crm_pipeline SET is_default = 0 WHERE is_default = 1");
        verify(tenantJdbcTemplate).update(
                "INSERT INTO crm_pipeline (pipeline_name, is_default) VALUES (?, ?)",
                "Enterprise Sales",
                1);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createStageUsesNextSortOrderAndReadsStageFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        PipelineService service = new PipelineService(tenantJdbcTemplateProvider);
        PipelineStageCreateRequest request = new PipelineStageCreateRequest();
        request.setStageName("Contract Review");
        request.setWinProbability(80);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline WHERE id = ?",
                Integer.class,
                9L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM crm_pipeline_stage WHERE pipeline_id = ?",
                Integer.class,
                9L)).thenReturn(5);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(15L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(15L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(15L);
                    when(resultSet.getLong("pipeline_id")).thenReturn(9L);
                    when(resultSet.getString("stage_name")).thenReturn("Contract Review");
                    when(resultSet.getInt("win_probability")).thenReturn(80);
                    when(resultSet.getInt("sort_order")).thenReturn(5);
                    return mapper.mapRow(resultSet, 0);
                });

        PipelineStageResponse response = service.createStage(9L, request);

        assertNotNull(response);
        assertEquals(15L, response.getId());
        assertEquals(9L, response.getPipelineId());
        assertEquals("Contract Review", response.getStageName());
        assertEquals(80, response.getWinProbability());
        assertEquals(5, response.getSortOrder());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_pipeline_stage (pipeline_id, stage_name, win_probability, sort_order) " +
                        "VALUES (?, ?, ?, ?)"),
                eq(9L),
                eq("Contract Review"),
                eq(80),
                eq(5));
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
