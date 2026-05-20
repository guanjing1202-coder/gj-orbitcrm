package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealResponse;
import com.orbitcrm.crm.api.DealUpdateRequest;
import com.orbitcrm.crm.api.PipelineStageBoardResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DealService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public DealService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<DealResponse> listDeals(String status, Long pipelineId, Long customerId, Long ownerUserId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                        "FROM crm_deal WHERE status <> 'DELETED'");
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            values.add(status);
        }
        if (pipelineId != null) {
            sql.append(" AND pipeline_id = ?");
            values.add(pipelineId);
        }
        if (customerId != null) {
            sql.append(" AND customer_id = ?");
            values.add(customerId);
        }
        if (ownerUserId != null) {
            sql.append(" AND owner_user_id = ?");
            values.add(ownerUserId);
        }
        sql.append(" ORDER BY id DESC LIMIT 100");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapDeal(rs), values.toArray());
    }

    public List<DealResponse> listDeletedDeals() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                        "FROM crm_deal WHERE status = 'DELETED' ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapDeal(rs));
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
        return getDeal(jdbcTemplate, id);
    }

    public List<PipelineStageBoardResponse> board(Long pipelineId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        Long resolvedPipelineId = pipelineId == null ? defaultPipelineId(jdbcTemplate) : pipelineId;
        Map<Long, PipelineStageBoardResponse> stages = new LinkedHashMap<Long, PipelineStageBoardResponse>();
        jdbcTemplate.query(
                "SELECT id, stage_name, win_probability, sort_order FROM crm_pipeline_stage " +
                        "WHERE pipeline_id = ? ORDER BY sort_order, id",
                rs -> {
                    PipelineStageBoardResponse stage = new PipelineStageBoardResponse();
                    stage.setStageId(rs.getLong("id"));
                    stage.setStageName(rs.getString("stage_name"));
                    stage.setWinProbability(rs.getInt("win_probability"));
                    stage.setSortOrder(rs.getInt("sort_order"));
                    stages.put(stage.getStageId(), stage);
                },
                resolvedPipelineId
            );
        jdbcTemplate.query(
                "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                        "FROM crm_deal WHERE pipeline_id = ? AND status = 'OPEN' ORDER BY id DESC",
                rs -> {
                    DealResponse deal = mapDeal(rs);
                    PipelineStageBoardResponse stage = stages.get(deal.getStageId());
                    if (stage != null) {
                        stage.getDeals().add(deal);
                        stage.setTotalAmountCent(stage.getTotalAmountCent() + deal.getAmountCent());
                    }
                },
                resolvedPipelineId
            );
        return new java.util.ArrayList<PipelineStageBoardResponse>(stages.values());
    }

    public DealResponse getDeal(Long id) {
        return getDeal(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    @OperationLog(action = "DEAL_UPDATE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse updateDeal(Long id, DealUpdateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        DealResponse existing = getDeal(jdbcTemplate, id);
        Long resolvedPipelineId = request.getPipelineId() == null ? existing.getPipelineId() : request.getPipelineId();
        Long resolvedStageId = request.getStageId();
        if (request.getPipelineId() != null && request.getStageId() == null
                && !request.getPipelineId().equals(existing.getPipelineId())) {
            resolvedStageId = firstStageId(jdbcTemplate, resolvedPipelineId);
        }
        if (resolvedStageId != null) {
            assertStageBelongsToPipeline(jdbcTemplate, resolvedPipelineId, resolvedStageId);
        }

        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE crm_deal SET ");
        boolean hasUpdate = false;
        if (StringUtils.hasText(request.getDealName())) {
            hasUpdate = appendAssignment(sql, hasUpdate, "deal_name = ?");
            values.add(request.getDealName());
        }
        if (request.getCustomerId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "customer_id = ?");
            values.add(request.getCustomerId());
        }
        if (request.getPipelineId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "pipeline_id = ?");
            values.add(request.getPipelineId());
        }
        if (resolvedStageId != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "stage_id = ?");
            values.add(resolvedStageId);
        }
        if (request.getAmountCent() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "amount_cent = ?");
            values.add(request.getAmountCent());
        }
        if (request.getExpectedCloseDate() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "expected_close_date = ?");
            values.add(Date.valueOf(request.getExpectedCloseDate()));
        }
        if (request.getOwnerUserId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "owner_user_id = ?");
            values.add(request.getOwnerUserId());
        }
        if (!hasUpdate) {
            return existing;
        }
        sql.append(" WHERE id = ? AND status <> 'DELETED'");
        values.add(id);
        jdbcTemplate.update(sql.toString(), values.toArray());
        return getDeal(jdbcTemplate, id);
    }

    @OperationLog(action = "DEAL_MOVE_STAGE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse moveStage(Long id, Long stageId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        DealResponse deal = getDeal(jdbcTemplate, id);
        assertStageBelongsToPipeline(jdbcTemplate, deal.getPipelineId(), stageId);
        jdbcTemplate.update("UPDATE crm_deal SET stage_id = ? WHERE id = ? AND status <> 'DELETED'", stageId, id);
        return getDeal(jdbcTemplate, id);
    }

    @OperationLog(action = "DEAL_ASSIGN", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse assignDeal(Long id, Long ownerUserId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_deal SET owner_user_id = ? WHERE id = ? AND status <> 'DELETED'",
                ownerUserId,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deal not found");
        }
        return getDeal(jdbcTemplate, id);
    }

    @OperationLog(action = "DEAL_WIN", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse winDeal(Long id) {
        return updateDealStatus(id, "WON");
    }

    @OperationLog(action = "DEAL_LOSE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse loseDeal(Long id) {
        return updateDealStatus(id, "LOST");
    }

    @OperationLog(action = "DEAL_REOPEN", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse reopenDeal(Long id) {
        return updateDealStatus(id, "OPEN");
    }

    @OperationLog(action = "DEAL_DELETE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse deleteDeal(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_deal SET status = 'DELETED' WHERE id = ? AND status <> 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deal not found");
        }
        return getDealIncludingDeleted(jdbcTemplate, id);
    }

    @OperationLog(action = "DEAL_RESTORE", targetType = "crm_deal", targetIdArg = 0)
    public DealResponse restoreDeal(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_deal SET status = 'OPEN' WHERE id = ? AND status = 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted deal not found");
        }
        return getDeal(jdbcTemplate, id);
    }

    private DealResponse updateDealStatus(Long id, String status) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_deal SET status = ? WHERE id = ? AND status <> 'DELETED'",
                status,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deal not found");
        }
        return getDeal(jdbcTemplate, id);
    }

    private DealResponse getDeal(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                            "FROM crm_deal WHERE id = ? AND status <> 'DELETED'",
                    (rs, rowNum) -> mapDeal(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deal not found", ex);
        }
    }

    private DealResponse getDealIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, deal_name, customer_id, pipeline_id, stage_id, amount_cent, expected_close_date, owner_user_id, status, create_time " +
                            "FROM crm_deal WHERE id = ?",
                    (rs, rowNum) -> mapDeal(rs),
                    id
                );
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
                Long.class,
                pipelineId
            );
    }

    private void assertStageBelongsToPipeline(JdbcTemplate jdbcTemplate, Long pipelineId, Long stageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_pipeline_stage WHERE pipeline_id = ? AND id = ?",
                Integer.class,
                pipelineId,
                stageId
            );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stage does not belong to pipeline");
        }
    }

    private boolean appendAssignment(StringBuilder sql, boolean hasUpdate, String assignment) {
        if (hasUpdate) {
            sql.append(", ");
        }
        sql.append(assignment);
        return true;
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
