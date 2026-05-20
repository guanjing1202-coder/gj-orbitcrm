package com.orbitcrm.task.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.task.api.TaskCreateRequest;
import com.orbitcrm.task.api.TaskResponse;
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

    public List<TaskResponse> listTasks(String status, Long assigneeUserId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        if (StringUtils.hasText(status) && assigneeUserId != null) {
            return jdbcTemplate.query(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE status = ? AND assignee_user_id = ? ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100",
                    (rs, rowNum) -> mapTask(rs),
                    status,
                    assigneeUserId
                );
        }
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE status = ? ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100",
                    (rs, rowNum) -> mapTask(rs),
                    status
                );
        }
        if (assigneeUserId != null) {
            return jdbcTemplate.query(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE assignee_user_id = ? ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100",
                    (rs, rowNum) -> mapTask(rs),
                    assigneeUserId
                );
        }
        return jdbcTemplate.query(
                "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                        "FROM crm_task ORDER BY due_time IS NULL, due_time, id DESC LIMIT 100",
                (rs, rowNum) -> mapTask(rs));
    }

    @OperationLog(action = "TASK_CREATE", targetType = "crm_task")
    public TaskResponse createTask(TaskCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        LocalDateTime remindTime = request.getDueTime() == null
                ? null
                : request.getDueTime().minusMinutes(defaultReminderMinutes);
        jdbcTemplate.update(
                "INSERT INTO crm_task (title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 'TODO')",
                request.getTitle(),
                request.getRelatedType(),
                request.getRelatedId(),
                request.getAssigneeUserId(),
                request.getDueTime() == null ? null : Timestamp.valueOf(request.getDueTime()),
                remindTime == null ? null : Timestamp.valueOf(remindTime));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getTask(id);
    }

    @OperationLog(action = "TASK_COMPLETE", targetType = "crm_task", targetIdArg = 0)
    public TaskResponse completeTask(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_task SET status = 'DONE' WHERE id = ? AND status <> 'DONE'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found or already done");
        }
        return getTask(id);
    }

    private TaskResponse getTask(Long id) {
        try {
            return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                    "SELECT id, title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status, create_time " +
                            "FROM crm_task WHERE id = ?",
                    (rs, rowNum) -> mapTask(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found", ex);
        }
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
