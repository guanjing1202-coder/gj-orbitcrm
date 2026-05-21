package com.orbitcrm.system.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.system.api.OperationLogResponse;
import com.orbitcrm.system.api.OperationLogStatResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public OperationLogService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<OperationLogResponse> listLogs(String action,
                                               Long userId,
                                               String targetType,
                                               String targetId,
                                               LocalDateTime startTime,
                                               LocalDateTime endTime,
                                               Integer limit) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, action, target_type, target_id, detail_json, create_time " +
                        "FROM sys_operation_log WHERE 1 = 1");
        appendFilters(sql, values, action, userId, targetType, targetId, startTime, endTime);
        sql.append(" ORDER BY id DESC LIMIT ").append(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapLog(
                rs.getLong("id"),
                longOrNull(rs.getObject("user_id")),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("detail_json"),
                rs.getTimestamp("create_time")), values.toArray());
    }

    public List<OperationLogStatResponse> actionStats(LocalDateTime startTime,
                                                      LocalDateTime endTime,
                                                      Integer limit) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(
                "SELECT action, COUNT(1) AS total, MAX(create_time) AS latest_time " +
                        "FROM sys_operation_log WHERE 1 = 1");
        appendTimeFilters(sql, values, startTime, endTime);
        sql.append(" GROUP BY action ORDER BY total DESC, action LIMIT ").append(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            OperationLogStatResponse response = new OperationLogStatResponse();
            response.setAction(rs.getString("action"));
            response.setTotal(rs.getLong("total"));
            response.setLatestTime(toLocalDateTime(rs.getTimestamp("latest_time")));
            return response;
        }, values.toArray());
    }

    private void appendFilters(StringBuilder sql,
                               List<Object> values,
                               String action,
                               Long userId,
                               String targetType,
                               String targetId,
                               LocalDateTime startTime,
                               LocalDateTime endTime) {
        if (StringUtils.hasText(action)) {
            sql.append(" AND action = ?");
            values.add(action);
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
            values.add(userId);
        }
        if (StringUtils.hasText(targetType)) {
            sql.append(" AND target_type = ?");
            values.add(targetType);
        }
        if (StringUtils.hasText(targetId)) {
            sql.append(" AND target_id = ?");
            values.add(targetId);
        }
        appendTimeFilters(sql, values, startTime, endTime);
    }

    private void appendTimeFilters(StringBuilder sql,
                                   List<Object> values,
                                   LocalDateTime startTime,
                                   LocalDateTime endTime) {
        if (startTime != null) {
            sql.append(" AND create_time >= ?");
            values.add(Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            sql.append(" AND create_time <= ?");
            values.add(Timestamp.valueOf(endTime));
        }
    }

    private int safeLimit(Integer limit) {
        return limit == null ? 100 : Math.max(1, Math.min(limit, 500));
    }

    private OperationLogResponse mapLog(Long id, Long userId, String action, String targetType,
                                        String targetId, String detailJson, Timestamp createTime) {
        OperationLogResponse response = new OperationLogResponse();
        response.setId(id);
        response.setUserId(userId);
        response.setAction(action);
        response.setTargetType(targetType);
        response.setTargetId(targetId);
        response.setDetailJson(detailJson);
        response.setCreateTime(toLocalDateTime(createTime));
        return response;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
