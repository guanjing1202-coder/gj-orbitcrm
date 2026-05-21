package com.orbitcrm.message.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.message.api.NoticeCreateRequest;
import com.orbitcrm.message.api.NoticeResponse;
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

@Service
public class NoticeService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public NoticeService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<NoticeResponse> listNotices(String status, String noticeType, Integer limit) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, notice_type, sender_user_id, status, NULL AS read_time, create_time " +
                        "FROM sys_notice WHERE 1 = 1");
        java.util.List<Object> values = new java.util.ArrayList<Object>();
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            values.add(status);
        } else {
            sql.append(" AND status <> 'DELETED'");
        }
        if (StringUtils.hasText(noticeType)) {
            sql.append(" AND notice_type = ?");
            values.add(noticeType);
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(safeLimit(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapNotice(rs), values.toArray());
    }

    public List<NoticeResponse> listMyNotices(Boolean unreadOnly) {
        Long userId = CurrentUserContext.require().getUserId();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        if (Boolean.TRUE.equals(unreadOnly)) {
            return jdbcTemplate.query(
                    "SELECT n.id, n.title, n.content, n.notice_type, n.sender_user_id, n.status, r.read_time, n.create_time " +
                            "FROM sys_notice n JOIN sys_notice_receiver r ON n.id = r.notice_id " +
                            "WHERE n.status = 'ACTIVE' AND r.user_id = ? AND r.read_time IS NULL " +
                            "ORDER BY n.id DESC LIMIT 200",
                    (rs, rowNum) -> mapNotice(rs),
                    userId
                );
        }
        return jdbcTemplate.query(
                "SELECT n.id, n.title, n.content, n.notice_type, n.sender_user_id, n.status, r.read_time, n.create_time " +
                        "FROM sys_notice n JOIN sys_notice_receiver r ON n.id = r.notice_id " +
                        "WHERE n.status = 'ACTIVE' AND r.user_id = ? ORDER BY n.id DESC LIMIT 200",
                (rs, rowNum) -> mapNotice(rs),
                userId
            );
    }

    public Integer unreadCount() {
        Long userId = CurrentUserContext.require().getUserId();
        Integer count = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM sys_notice n JOIN sys_notice_receiver r ON n.id = r.notice_id " +
                        "WHERE n.status = 'ACTIVE' AND r.user_id = ? AND r.read_time IS NULL",
                Integer.class,
                userId
            );
        return count == null ? 0 : count;
    }

    @OperationLog(action = "NOTICE_CREATE", targetType = "sys_notice")
    public NoticeResponse createNotice(NoticeCreateRequest request) {
        return createNoticeInternal(request, currentUserId());
    }

    public NoticeResponse createNoticeFromEvent(NoticeEvent event) {
        if (!StringUtils.hasText(event.getTenantCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantCode is required");
        }
        TenantContext.setTenantCode(event.getTenantCode());
        try {
            NoticeCreateRequest request = new NoticeCreateRequest();
            request.setTitle(event.getTitle());
            request.setContent(event.getContent());
            request.setNoticeType(event.getNoticeType());
            request.setReceiverUserIds(event.getReceiverUserIds());
            return createNoticeInternal(request, null);
        } finally {
            TenantContext.clear();
        }
    }

    public NoticeResponse getNotice(Long id) {
        return getNotice(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    public NoticeResponse getMyNotice(Long id) {
        Long userId = CurrentUserContext.require().getUserId();
        return getMyNotice(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id, userId);
    }

    @OperationLog(action = "NOTICE_READ", targetType = "sys_notice", targetIdArg = 0)
    public NoticeResponse markRead(Long id) {
        Long userId = CurrentUserContext.require().getUserId();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_notice_receiver SET read_time = NOW() WHERE notice_id = ? AND user_id = ? AND read_time IS NULL",
                id,
                userId);
        if (updated == 0) {
            ensureNoticeVisibleToUser(jdbcTemplate, id, userId);
        }
        return getMyNotice(jdbcTemplate, id, userId);
    }

    @OperationLog(action = "NOTICE_UNREAD", targetType = "sys_notice", targetIdArg = 0)
    public NoticeResponse markUnread(Long id) {
        Long userId = CurrentUserContext.require().getUserId();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_notice_receiver SET read_time = NULL WHERE notice_id = ? AND user_id = ? AND read_time IS NOT NULL",
                id,
                userId);
        if (updated == 0) {
            ensureNoticeVisibleToUser(jdbcTemplate, id, userId);
        }
        return getMyNotice(jdbcTemplate, id, userId);
    }

    @OperationLog(action = "NOTICE_READ_ALL", targetType = "sys_notice")
    public Integer markAllRead() {
        Long userId = CurrentUserContext.require().getUserId();
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().update(
                "UPDATE sys_notice_receiver r JOIN sys_notice n ON r.notice_id = n.id " +
                        "SET r.read_time = NOW() WHERE r.user_id = ? AND r.read_time IS NULL AND n.status = 'ACTIVE'",
                userId);
    }

    @OperationLog(action = "NOTICE_DELETE", targetType = "sys_notice", targetIdArg = 0)
    public NoticeResponse deleteNotice(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_notice SET status = 'DELETED' WHERE id = ? AND status = 'ACTIVE'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
        }
        return getNoticeIncludingDeleted(jdbcTemplate, id);
    }

    private NoticeResponse createNoticeInternal(NoticeCreateRequest request, Long senderUserId) {
        if (!StringUtils.hasText(request.getTitle()) || !StringUtils.hasText(request.getContent())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title and content are required");
        }
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                request.getTitle(),
                request.getContent(),
                StringUtils.hasText(request.getNoticeType()) ? request.getNoticeType() : "SYSTEM",
                senderUserId);
        Long noticeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        List<Long> receiverUserIds = resolveReceivers(jdbcTemplate, request.getReceiverUserIds());
        if (receiverUserIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receiver users are required");
        }
        for (Long receiverUserId : receiverUserIds) {
            jdbcTemplate.update(
                    "INSERT INTO sys_notice_receiver (notice_id, user_id) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)",
                    noticeId,
                    receiverUserId);
        }
        return getNotice(jdbcTemplate, noticeId);
    }

    private List<Long> resolveReceivers(JdbcTemplate jdbcTemplate, List<Long> receiverUserIds) {
        if (receiverUserIds != null && !receiverUserIds.isEmpty()) {
            return receiverUserIds;
        }
        return jdbcTemplate.query(
                "SELECT id FROM sys_user WHERE status = 'ACTIVE'",
                (rs, rowNum) -> rs.getLong("id"));
    }

    private NoticeResponse getNotice(JdbcTemplate jdbcTemplate, Long id) {
        List<NoticeResponse> notices = jdbcTemplate.query(
                "SELECT id, title, content, notice_type, sender_user_id, status, NULL AS read_time, create_time " +
                        "FROM sys_notice WHERE id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> mapNotice(rs),
                id
            );
        if (notices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
        }
        return notices.get(0);
    }

    private NoticeResponse getNoticeIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        List<NoticeResponse> notices = jdbcTemplate.query(
                "SELECT id, title, content, notice_type, sender_user_id, status, NULL AS read_time, create_time " +
                        "FROM sys_notice WHERE id = ?",
                (rs, rowNum) -> mapNotice(rs),
                id
            );
        if (notices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
        }
        return notices.get(0);
    }

    private NoticeResponse getMyNotice(JdbcTemplate jdbcTemplate, Long id, Long userId) {
        List<NoticeResponse> notices = jdbcTemplate.query(
                "SELECT n.id, n.title, n.content, n.notice_type, n.sender_user_id, n.status, r.read_time, n.create_time " +
                        "FROM sys_notice n JOIN sys_notice_receiver r ON n.id = r.notice_id " +
                        "WHERE n.id = ? AND r.user_id = ? AND n.status = 'ACTIVE'",
                (rs, rowNum) -> mapNotice(rs),
                id,
                userId
            );
        if (notices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
        }
        return notices.get(0);
    }

    private void ensureNoticeVisibleToUser(JdbcTemplate jdbcTemplate, Long id, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_notice n JOIN sys_notice_receiver r ON n.id = r.notice_id " +
                        "WHERE n.id = ? AND r.user_id = ? AND n.status = 'ACTIVE'",
                Integer.class,
                id,
                userId
            );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "notice not found");
        }
    }

    private int safeLimit(Integer limit) {
        return limit == null ? 200 : Math.max(1, Math.min(limit, 500));
    }

    private NoticeResponse mapNotice(ResultSet rs) throws SQLException {
        NoticeResponse response = new NoticeResponse();
        response.setId(rs.getLong("id"));
        response.setTitle(rs.getString("title"));
        response.setContent(rs.getString("content"));
        response.setNoticeType(rs.getString("notice_type"));
        response.setSenderUserId(longOrNull(rs.getObject("sender_user_id")));
        response.setStatus(rs.getString("status"));
        response.setReadTime(toLocalDateTime(rs.getTimestamp("read_time")));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private Long currentUserId() {
        CurrentUser currentUser = CurrentUserContext.get();
        return currentUser == null ? null : currentUser.getUserId();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
