package com.orbitcrm.task.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class TaskReminderService {
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final TaskNoticePublisher taskNoticePublisher;
    private final int reminderBatchSize;

    public TaskReminderService(JdbcTemplate platformJdbcTemplate,
                               TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                               TaskNoticePublisher taskNoticePublisher,
                               @Value("${orbit.task.reminder-batch-size:100}") int reminderBatchSize) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.taskNoticePublisher = taskNoticePublisher;
        this.reminderBatchSize = reminderBatchSize;
    }

    public int dispatchDueReminders() {
        List<String> tenantCodes = platformJdbcTemplate.query(
                "SELECT d.tenant_code FROM platform_tenant_database d " +
                        "JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE d.status = 'ACTIVE' AND t.status = 'ACTIVE'",
                (rs, rowNum) -> rs.getString("tenant_code"));
        int total = 0;
        for (String tenantCode : tenantCodes) {
            TenantContext.setTenantCode(tenantCode);
            try {
                total += dispatchTenantDueReminders(tenantCode);
            } finally {
                TenantContext.clear();
            }
        }
        return total;
    }

    private int dispatchTenantDueReminders(String tenantCode) {
        JdbcTemplate tenantJdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        List<TaskReminderCandidate> tasks = tenantJdbcTemplate.query(
                "SELECT id, title, assignee_user_id, due_time FROM crm_task " +
                        "WHERE status = 'TODO' AND remind_status = 'PENDING' " +
                        "AND remind_time IS NOT NULL AND remind_time <= NOW() " +
                        "AND assignee_user_id IS NOT NULL ORDER BY remind_time, id LIMIT " + safeBatchSize(),
                (rs, rowNum) -> new TaskReminderCandidate(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getLong("assignee_user_id"),
                        toLocalDateTime(rs.getTimestamp("due_time"))));
        int sent = 0;
        for (TaskReminderCandidate task : tasks) {
            publishReminder(tenantCode, task);
            tenantJdbcTemplate.update(
                    "UPDATE crm_task SET remind_status = 'SENT' WHERE id = ? AND remind_status = 'PENDING'",
                    task.getId());
            sent++;
        }
        return sent;
    }

    private void publishReminder(String tenantCode, TaskReminderCandidate task) {
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode(tenantCode);
        event.setTitle("Task reminder");
        event.setContent("Task \"" + task.getTitle() + "\" is due at " + task.getDueTime());
        event.setNoticeType("TASK_REMINDER");
        event.setReceiverUserIds(Arrays.asList(task.getAssigneeUserId()));
        taskNoticePublisher.publish(event);
    }

    private int safeBatchSize() {
        return Math.max(1, Math.min(reminderBatchSize, 500));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static class TaskReminderCandidate {
        private final Long id;
        private final String title;
        private final Long assigneeUserId;
        private final LocalDateTime dueTime;

        TaskReminderCandidate(Long id, String title, Long assigneeUserId, LocalDateTime dueTime) {
            this.id = id;
            this.title = title;
            this.assigneeUserId = assigneeUserId;
            this.dueTime = dueTime;
        }

        Long getId() {
            return id;
        }

        String getTitle() {
            return title;
        }

        Long getAssigneeUserId() {
            return assigneeUserId;
        }

        LocalDateTime getDueTime() {
            return dueTime;
        }
    }
}
