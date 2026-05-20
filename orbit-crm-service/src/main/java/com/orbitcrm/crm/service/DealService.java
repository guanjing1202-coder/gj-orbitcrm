package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealResponse;
import com.orbitcrm.crm.api.PipelineStageBoardResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DealService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public DealService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    @OperationLog(action = "DEAL_CREATE", targetType = "crm_deal")
    public DealResponse createDeal(DealCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        Long pipelineId = request.getPipelineId() == null ? defaultPipelineId(jdbcTemplate) : request.getPipelineId();
        Long stageId = request.getStageId() == null ? firstStageId(jdbcTemplate, pipelineId) : request.getStageId();
        assertStageBelongsToPipeline(jdbcTemplate, pipelineId, stageId);
        jdbcTemplate.update(
                "INSERT INTO crm_deal (deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN')",
                request.getDealName(),
                request.getCustomerId(),
                pipelineId,
                stageId,
                request.getAmountCent() == null ? 0L : request.getAmountCent(),
                request.getExpectedCloseDate() == null ? null : Date.valueOf(request.getExpectedCloseDate()),
                request.getOwnerUserId());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getDeal(id);
    }

    public List<PipelineStageBoardResponse> board(Long pipelineId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        Long resolvedPipelineId = pipelineId == null ? defaultPipelineId(jdbcTemplate) : pipelineId;
        Map<Long, PipelineStageBoardResponse> stages = new LinkedHashMap<Long, PipelineStageBoardResponse>();
        jdbcTemplate.query(
                "SELECT id, stage_name, win_probability, sort_order FROM crm_pipeline_stage " +
                        "WHERE pipeline_id = ? ORDER BY sort_order, id",
                new Object[]{resolvedPipelineId},
                rs -> {
                    PipelineStageBoardResponse stage = new PipelineStageBoardResponse();
                    stage.setStageId(rs.getLong("id"));
                    stage.setStageName(rs.getString("stage_name"));
                    stage.setWinProbability(rs.getInt("win_probability"));
                    stage.setSortOrder(rs.getInt("sort_order"));
                    stages.put(stage.getStageId(), stage);
                });
        jdbcTemplate.query(
                "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                        "FROM crm_deal WHERE pipeline_id = ? AND status = 'OPEN' ORDER BY id DESC",
                new Object[]{resolvedPipelineId},
                rs -> {
                    DealResponse deal = mapDeal(rs);
                    PipelineStageBoardResponse stage = stages.get(deal.getStageId());
                    if (stage != null) {
                        stage.getDeals().add(deal);
                        stage.setTotalAmountCent(stage.getTotalAmountCent() + deal.getAmountCent());
                    }
                });
        return new java.util.ArrayList<PipelineStageBoardResponse>(stages.values());
    }

    @OperationLog(action = "DEAL_MOVE_STAGE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse moveStage(Long id, Long stageId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        DealResponse deal = getDeal(id);
        assertStageBelongsToPipeline(jdbcTemplate, deal.getPipelineId(), stageId);
        jdbcTemplate.update("UPDATE crm_deal SET stage_id = ? WHERE id = ?", stageId, id);
        return getDeal(id);
    }

    private DealResponse getDeal(Long id) {
        try {
            return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                    "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                            "FROM crm_deal WHERE id = ?",
                    new Object[]{id},
                    (rs, rowNum) -> mapDeal(rs));
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deal not found", ex);
        }
    }

    private Long defaultPipelineId(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM crm_pipeline WHERE is_default = 1 ORDER BY id LIMIT 1",
                Long.class);
    }

    private Long firstStageId(JdbcTemplate jdbcTemplate, Long pipelineId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM crm_pipeline_stage WHERE pipeline_id = ? ORDER BY sort_order, id LIMIT 1",
                new Object[]{pipelineId},
                Long.class);
    }

    private void assertStageBelongsToPipeline(JdbcTemplate jdbcTemplate, Long pipelineId, Long stageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline_stage WHERE pipeline_id = ? AND id = ?",
                new Object[]{pipelineId, stageId},
                Integer.class);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stage does not belong to pipeline");
        }
    }

    private DealResponse mapDeal(ResultSet rs) throws SQLException {
        DealResponse response = new DealResponse();
        response.setId(rs.getLong("id"));
        response.setDealName(rs.getString("deal_name"));
        response.setCustomerId(longOrNull(rs.getObject("customer_id")));
        response.setPipelineId(rs.getLong("pipeline_id"));
        response.setStageId(rs.getLong("stage_id"));
        response.setAmountCent(rs.getLong("amount_cent"));
        response.setExpectedCloseDate(toLocalDate(rs.getDate("expected_close_date")));
        response.setOwnerUserId(longOrNull(rs.getObject("owner_user_id")));
        response.setStatus(rs.getString("status"));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
