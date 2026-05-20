package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.crm.api.FollowRecordCreateRequest;
import com.orbitcrm.crm.api.FollowRecordResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class FollowRecordService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public FollowRecordService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<FollowRecordResponse> listFollowRecords(String relatedType, Long relatedId) {
        String normalizedType = normalizeRelatedType(relatedType);
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertRelatedExists(jdbcTemplate, normalizedType, relatedId);
        return jdbcTemplate.query(
                "SELECT id, related_type, related_id, content, next_follow_time, creator_user_id, create_time " +
                        "FROM crm_follow_record WHERE related_type = ? AND related_id = ? ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapFollowRecord(rs),
                normalizedType,
                relatedId
            );
    }

    @OperationLog(action = "FOLLOW_RECORD_CREATE", targetType = "crm_follow_record")
    public FollowRecordResponse createFollowRecord(FollowRecordCreateRequest request) {
        String normalizedType = normalizeRelatedType(request.getRelatedType());
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertRelatedExists(jdbcTemplate, normalizedType, request.getRelatedId());
        CurrentUser currentUser = CurrentUserContext.get();
        Long creatorUserId = currentUser == null ? null : currentUser.getUserId();
        jdbcTemplate.update(
                "INSERT INTO crm_follow_record (related_type, related_id, content, next_follow_time, creator_user_id) " +
                        "VALUES (?, ?, ?, ?, ?)",
                normalizedType,
                request.getRelatedId(),
                request.getContent(),
                request.getNextFollowTime() == null ? null : Timestamp.valueOf(request.getNextFollowTime()),
                creatorUserId);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getFollowRecord(jdbcTemplate, id);
    }

    private FollowRecordResponse getFollowRecord(Long id) {
        return getFollowRecord(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    private FollowRecordResponse getFollowRecord(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, related_type, related_id, content, next_follow_time, creator_user_id, create_time " +
                            "FROM crm_follow_record WHERE id = ?",
                    (rs, rowNum) -> mapFollowRecord(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "follow record not found", ex);
        }
    }

    private String normalizeRelatedType(String relatedType) {
        if (!StringUtils.hasText(relatedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedType is required");
        }
        String normalized = relatedType.trim().toUpperCase(Locale.ENGLISH);
        if (!"LEAD".equals(normalized) && !"CUSTOMER".equals(normalized) && !"DEAL".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedType must be LEAD, CUSTOMER, or DEAL");
        }
        return normalized;
    }

    private void assertRelatedExists(JdbcTemplate jdbcTemplate, String relatedType, Long relatedId) {
        if (relatedId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "relatedId is required");
        }
        String sql;
        if ("LEAD".equals(relatedType)) {
            sql = "SELECT COUNT(1) FROM crm_lead WHERE id = ? AND status <> 'DELETED'";
        } else if ("CUSTOMER".equals(relatedType)) {
            sql = "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'";
        } else {
            sql = "SELECT COUNT(1) FROM crm_deal WHERE id = ? AND status <> 'DELETED'";
        }
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, relatedId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "related business object not found");
        }
    }

    private FollowRecordResponse mapFollowRecord(ResultSet rs) throws SQLException {
        FollowRecordResponse response = new FollowRecordResponse();
        response.setId(rs.getLong("id"));
        response.setRelatedType(rs.getString("related_type"));
        response.setRelatedId(rs.getLong("related_id"));
        response.setContent(rs.getString("content"));
        response.setNextFollowTime(toLocalDateTime(rs.getTimestamp("next_follow_time")));
        response.setCreatorUserId(longOrNull(rs.getObject("creator_user_id")));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
