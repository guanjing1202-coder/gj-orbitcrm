package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.PipelineCreateRequest;
import com.orbitcrm.crm.api.PipelineResponse;
import com.orbitcrm.crm.api.PipelineStageCreateRequest;
import com.orbitcrm.crm.api.PipelineStageResponse;
import com.orbitcrm.crm.api.PipelineStageUpdateRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class PipelineService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public PipelineService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<PipelineResponse> listPipelines() {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<PipelineResponse> pipelines = jdbcTemplate.query(
                "SELECT id, pipeline_name, is_default FROM crm_pipeline ORDER BY is_default DESC, id",
                (rs, rowNum) -> mapPipeline(rs));
        for (PipelineResponse pipeline : pipelines) {
            pipeline.setStages(listStages(jdbcTemplate, pipeline.getId()));
        }
        return pipelines;
    }

    @OperationLog(action = "PIPELINE_CREATE", targetType = "crm_pipeline")
    public PipelineResponse createPipeline(PipelineCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        boolean defaultPipeline = Boolean.TRUE.equals(request.getDefaultPipeline());
        if (defaultPipeline) {
            clearDefaultPipeline(jdbcTemplate);
        }
        jdbcTemplate.update(
                "INSERT INTO crm_pipeline (pipeline_name, is_default) VALUES (?, ?)",
                request.getPipelineName(),
                defaultPipeline ? 1 : 0);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getPipeline(jdbcTemplate, id);
    }

    @OperationLog(action = "PIPELINE_SET_DEFAULT", targetType = "crm_pipeline", targetIdArg = 0)
    public PipelineResponse setDefaultPipeline(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertPipelineExists(jdbcTemplate, id);
        clearDefaultPipeline(jdbcTemplate);
        jdbcTemplate.update("UPDATE crm_pipeline SET is_default = 1 WHERE id = ?", id);
        return getPipeline(jdbcTemplate, id);
    }

    public List<PipelineStageResponse> listStages(Long pipelineId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertPipelineExists(jdbcTemplate, pipelineId);
        return listStages(jdbcTemplate, pipelineId);
    }

    @OperationLog(action = "PIPELINE_STAGE_CREATE", targetType = "crm_pipeline_stage")
    public PipelineStageResponse createStage(Long pipelineId, PipelineStageCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertPipelineExists(jdbcTemplate, pipelineId);
        int sortOrder = request.getSortOrder() == null ? nextSortOrder(jdbcTemplate, pipelineId) : request.getSortOrder();
        int winProbability = request.getWinProbability() == null ? 0 : request.getWinProbability();
        jdbcTemplate.update(
                "INSERT INTO crm_pipeline_stage (pipeline_id, stage_name, win_probability, sort_order) " +
                        "VALUES (?, ?, ?, ?)",
                pipelineId,
                request.getStageName(),
                winProbability,
                sortOrder);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getStage(jdbcTemplate, id);
    }

    @OperationLog(action = "PIPELINE_STAGE_UPDATE", targetType = "crm_pipeline_stage", targetIdArg = 0)
    public PipelineStageResponse updateStage(Long stageId, PipelineStageUpdateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        PipelineStageResponse stage = getStage(jdbcTemplate, stageId);
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE crm_pipeline_stage SET ");
        boolean hasUpdate = false;
        if (StringUtils.hasText(request.getStageName())) {
            hasUpdate = appendAssignment(sql, hasUpdate, "stage_name = ?");
            values.add(request.getStageName());
        }
        if (request.getWinProbability() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "win_probability = ?");
            values.add(request.getWinProbability());
        }
        if (request.getSortOrder() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "sort_order = ?");
            values.add(request.getSortOrder());
        }
        if (!hasUpdate) {
            return stage;
        }
        sql.append(" WHERE id = ?");
        values.add(stageId);
        jdbcTemplate.update(sql.toString(), values.toArray());
        return getStage(jdbcTemplate, stageId);
    }

    private PipelineResponse getPipeline(JdbcTemplate jdbcTemplate, Long id) {
        try {
            PipelineResponse response = jdbcTemplate.queryForObject(
                    "SELECT id, pipeline_name, is_default FROM crm_pipeline WHERE id = ?",
                    (rs, rowNum) -> mapPipeline(rs),
                    id);
            response.setStages(listStages(jdbcTemplate, id));
            return response;
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pipeline not found", ex);
        }
    }

    private PipelineStageResponse getStage(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, pipeline_id, stage_name, win_probability, sort_order " +
                            "FROM crm_pipeline_stage WHERE id = ?",
                    (rs, rowNum) -> mapStage(rs),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pipeline stage not found", ex);
        }
    }

    private List<PipelineStageResponse> listStages(JdbcTemplate jdbcTemplate, Long pipelineId) {
        return jdbcTemplate.query(
                "SELECT id, pipeline_id, stage_name, win_probability, sort_order " +
                        "FROM crm_pipeline_stage WHERE pipeline_id = ? ORDER BY sort_order, id",
                (rs, rowNum) -> mapStage(rs),
                pipelineId);
    }

    private void assertPipelineExists(JdbcTemplate jdbcTemplate, Long pipelineId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline WHERE id = ?",
                Integer.class,
                pipelineId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "pipeline not found");
        }
    }

    private void clearDefaultPipeline(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("UPDATE crm_pipeline SET is_default = 0 WHERE is_default = 1");
    }

    private int nextSortOrder(JdbcTemplate jdbcTemplate, Long pipelineId) {
        Integer sortOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM crm_pipeline_stage WHERE pipeline_id = ?",
                Integer.class,
                pipelineId);
        return sortOrder == null ? 1 : sortOrder;
    }

    private boolean appendAssignment(StringBuilder sql, boolean hasUpdate, String assignment) {
        if (hasUpdate) {
            sql.append(", ");
        }
        sql.append(assignment);
        return true;
    }

    private PipelineResponse mapPipeline(ResultSet rs) throws SQLException {
        PipelineResponse response = new PipelineResponse();
        response.setId(rs.getLong("id"));
        response.setPipelineName(rs.getString("pipeline_name"));
        response.setDefaultPipeline(rs.getInt("is_default") == 1);
        return response;
    }

    private PipelineStageResponse mapStage(ResultSet rs) throws SQLException {
        PipelineStageResponse response = new PipelineStageResponse();
        response.setId(rs.getLong("id"));
        response.setPipelineId(rs.getLong("pipeline_id"));
        response.setStageName(rs.getString("stage_name"));
        response.setWinProbability(rs.getInt("win_probability"));
        response.setSortOrder(rs.getInt("sort_order"));
        return response;
    }
}
