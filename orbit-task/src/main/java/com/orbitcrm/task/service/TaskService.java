package com.orbitcrm.task.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.task.api.TaskCreateRequest;
import com.orbitcrm.task.api.TaskResponse;
import com.orbitcrm.task.api.TaskUpdateRequest;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final int defaultReminderMinutes;

    public TaskService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                       @Value("${orbit.task.default-reminder-minutes:30}") int defaultReminderMinutes) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.defaultReminderMinutes = defaultReminderMinutes;
    }

    public List<TaskResponse> listTasks(String status, Long assigneeUserId, String relatedType, Long relatedId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                        "FROM crm_task WHERE status <> 'DELETED'");
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            values.add(status);
        }
        if (assigneeUserId != null) {
            sql.append(" AND assignee_user_id = ?");
            values.add(assigneeUserId);
        }
        if (StringUtils.hasText(relatedType)) {
            sql.append(" AND related_type = ?");
            values.add(relatedType);
        }
        if (relatedId != null) {
            sql.append(" AND related_id = ?");
            values.add(relatedId);
        }
        sql.append(" ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTask(rs), values.toArray());
    }

    public List<TaskResponse> listDeletedTasks() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                        "FROM crm_task WHERE status = 'DELETED' ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100",
                (rs, rowNum) -> mapTask(rs));
    }

    @OperationLog(action = "TASK_CREATE", targetType = "crm_task")
    public TaskResponse createTask(TaskCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        LocalDateTime remindTime = calculateRemindTime(request.getDueTime());
        jdbcTemplate.update(
                "INSERT INTO crm_task (title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 'TODO')",
                request.getTitle(),
                request.getRelatedType(),
                request.getRelatedId(),
                request.getAssigneeUserId(),
                toTimestamp(request.getDueTime()),
                toTimestamp(remindTime));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getTask(jdbcTemplate, id);
    }

    public TaskResponse getTask(Long id) {
        return getTask(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    @OperationLog(action = "TASK_UPDATE", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse updateTask(Long id, TaskUpdateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        TaskResponse existing = getTask(jdbcTemplate, id);
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE crm_task SET ");
        boolean hasUpdate = false;
        if (StringUtils.hasText(request.getTitle())) {
            hasUpdate = appendAssignment(sql, hasUpdate, "title = ?");
            values.add(request.getTitle());
        }
        if (request.getRelatedType() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "related_type = ?");
            values.add(request.getRelatedType());
        }
        if (request.getRelatedId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "related_id = ?");
            values.add(request.getRelatedId());
        }
        if (request.getAssigneeUserId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "assignee_user_id = ?");
            values.add(request.getAssigneeUserId());
        }
        if (request.getDueTime() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "due_time = ?");
            values.add(toTimestamp(request.getDueTime()));
            hasUpdate = appendAssignment(sql, hasUpdate, "remind_time = ?");
            values.add(toTimestamp(calculateRemindTime(request.getDueTime())));
            hasUpdate = appendAssignment(sql, hasUpdate, "remind_status = 'PENDING'");
        }
        if (!hasUpdate) {
            return existing;
        }
        sql.append(" WHERE id = ? AND status <> 'DELETED'");
        values.add(id);
        jdbcTemplate.update(sql.toString(), values.toArray());
        return getTask(jdbcTemplate, id);
    }

    @OperationLog(action = "TASK_ASSIGN", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse assignTask(Long id, Long assigneeUserId) {
        if (assigneeUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assigneeUserId is required");
        }
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET assignee_user_id = ? WHERE id = ? AND status <> 'DELETED'",
                assigneeUserId,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found");
        }
        return getTask(jdbcTemplate, id);
    }

    @OperationLog(action = "TASK_COMPLETE", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse completeTask(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = 'DONE' WHERE id = ? AND status <> 'DELETED' AND status <> 'DONE'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found or already done");
        }
        return getTask(jdbcTemplate, id);
    }

    @OperationLog(action = "TASK_REOPEN", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse reopenTask(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = 'TODO', remind_status = 'PENDING' WHERE id = ? AND status <> 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found");
        }
        return getTask(jdbcTemplate, id);
    }

    @OperationLog(action = "TASK_CANCEL", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse cancelTask(Long id) {
        return updateTaskStatus(id, "CANCELED", "task not found");
    }

    @OperationLog(action = "TASK_DELETE", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse deleteTask(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = 'DELETED' WHERE id = ? AND status <> 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found");
        }
        return getTaskIncludingDeleted(jdbcTemplate, id);
    }

    @OperationLog(action = "TASK_RESTORE", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse restoreTask(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = 'TODO', remind_status = 'PENDING' WHERE id = ? AND status = 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted task not found");
        }
        return getTask(jdbcTemplate, id);
    }

    private TaskResponse updateTaskStatus(Long id, String status, String notFoundMessage) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = ? WHERE id = ? AND status <> 'DELETED'",
                status,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        return getTask(jdbcTemplate, id);
    }

    private TaskResponse getTask(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE id = ? AND status <> 'DELETED'",
                    (rs, rowNum) -> mapTask(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found", ex);
        }
    }

    private TaskResponse getTaskIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE id = ?",
                    (rs, rowNum) -> mapTask(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found", ex);
        }
    }

    private LocalDateTime calculateRemindTime(LocalDateTime dueTime) {
        return dueTime == null ? null : dueTime.minusMinutes(defaultReminderMinutes);
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private boolean appendAssignment(StringBuilder sql, boolean hasUpdate, String assignment) {
        if (hasUpdate) {
            sql.append(", ");
        }
        sql.append(assignment);
        return true;
    }

    private TaskResponse mapTask(ResultSet rs) throws SQLException {
        TaskResponse response = new TaskResponse();
        response.setId(rs.getLong("id"));
        response.setTitle(rs.getString("title"));
        response.setRelatedType(rs.getString("related_type"));
        response.setRelatedId(longOrNull(rs.getObject("related_id")));
        response.setAssigneeUserId(longOrNull(rs.getObject("assignee_user_id")));
        response.setDueTime(toLocalDateTime(rs.getTimestamp("due_time")));
        response.setRemindTime(toLocalDateTime(rs.getTimestamp("remind_time")));
        response.setRemindStatus(rs.getString("remind_status"));
        response.setStatus(rs.getString("status"));
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
