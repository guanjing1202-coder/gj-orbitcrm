package com.orbitcrm.system.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.system.api.OperationLogResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationLogService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public OperationLogService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<OperationLogResponse> listLogs(String action, Integer limit) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int safeLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 500));
        if (StringUtils.hasText(action)) {
            return jdbcTemplate.query(
                    "SELECT id, user_id, action, target_type, target_id, detail_json, create_time " +
                            "FROM sys_operation_log WHERE action = ? ORDER BY id DESC LIMIT " + safeLimit,
                    new Object[]{action},
                    (rs, rowNum) -> mapLog(rs.getLong("id"),
                            longOrNull(rs.getObject("user_id")),
                            rs.getString("action"),
                            rs.getString("target_type"),
                            rs.getString("target_id"),
                            rs.getString("detail_json"),
                            rs.getTimestamp("create_time")));
        }
        return jdbcTemplate.query(
                "SELECT id, user_id, action, target_type, target_id, detail_json, create_time " +
                        "FROM sys_operation_log ORDER BY id DESC LIMIT " + safeLimit,
                (rs, rowNum) -> mapLog(rs.getLong("id"),
                        longOrNull(rs.getObject("user_id")),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("detail_json"),
                        rs.getTimestamp("create_time")));
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
