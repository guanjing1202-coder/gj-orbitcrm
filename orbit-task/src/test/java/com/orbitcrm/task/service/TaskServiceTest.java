package com.orbitcrm.task.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.task.api.TaskCreateRequest;
import com.orbitcrm.task.api.TaskResponse;
import com.orbitcrm.task.api.TaskUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createTaskReadsCreatedTaskFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        TaskService service = new TaskService(tenantJdbcTemplateProvider, 30);
        LocalDateTime dueTime = LocalDateTime.of(2026, 5, 21, 10, 0);
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTitle("Call customer");
        request.setRelatedType("CUSTOMER");
        request.setRelatedId(66L);
        request.setAssigneeUserId(15L);
        request.setDueTime(dueTime);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(33L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(33L)))
                .thenAnswer(invocation -> mapTask(invocation.getArgument(1), "Call customer", "TODO", 15L, dueTime));

        TaskResponse response = service.createTask(request);

        assertNotNull(response);
        assertEquals(33L, response.getId());
        assertEquals("Call customer", response.getTitle());
        assertEquals("CUSTOMER", response.getRelatedType());
        assertEquals(66L, response.getRelatedId());
        assertEquals(15L, response.getAssigneeUserId());
        assertEquals(dueTime, response.getDueTime());
        assertEquals(dueTime.minusMinutes(30), response.getRemindTime());
        assertEquals("PENDING", response.getRemindStatus());
        assertEquals("TODO", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_task (title, related_type, related_id, assignee_user_id, due_time, remind_time, remind_status, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'PENDING', 'TODO')"),
                eq("Call customer"),
                eq("CUSTOMER"),
                eq(66L),
                eq(15L),
                eq(Timestamp.valueOf(dueTime)),
                eq(Timestamp.valueOf(dueTime.minusMinutes(30))));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void updateTaskRecalculatesReminderAndReadsUpdatedTaskFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        TaskService service = new TaskService(tenantJdbcTemplateProvider, 30);
        LocalDateTime dueTime = LocalDateTime.of(2026, 5, 22, 14, 0);
        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setTitle("Prepare renewal proposal");
        request.setAssigneeUserId(18L);
        request.setDueTime(dueTime);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(33L)))
                .thenAnswer(invocation -> mapTask(invocation.getArgument(1), "Prepare renewal proposal", "TODO", 18L, dueTime));

        TaskResponse response = service.updateTask(33L, request);

        assertNotNull(response);
        assertEquals("Prepare renewal proposal", response.getTitle());
        assertEquals(18L, response.getAssigneeUserId());
        assertEquals(dueTime.minusMinutes(30), response.getRemindTime());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("UPDATE crm_task SET title = ?, assignee_user_id = ?, due_time = ?, remind_time = ?, remind_status = 'PENDING' WHERE id = ? AND status <> 'DELETED'"),
                eq("Prepare renewal proposal"),
                eq(18L),
                eq(Timestamp.valueOf(dueTime)),
                eq(Timestamp.valueOf(dueTime.minusMinutes(30))),
                eq(33L));
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completeTaskReadsCompletedTaskFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        TaskService service = new TaskService(tenantJdbcTemplateProvider, 30);
        LocalDateTime dueTime = LocalDateTime.of(2026, 5, 21, 10, 0);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE crm_task SET status = 'DONE' WHERE id = ? AND status <> 'DELETED' AND status <> 'DONE'",
                33L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(33L)))
                .thenAnswer(invocation -> mapTask(invocation.getArgument(1), "Call customer", "DONE", 15L, dueTime));

        TaskResponse response = service.completeTask(33L);

        assertNotNull(response);
        assertEquals("DONE", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                "UPDATE crm_task SET status = 'DONE' WHERE id = ? AND status <> 'DELETED' AND status <> 'DONE'",
                33L);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    private TaskResponse mapTask(RowMapper<TaskResponse> mapper,
                                 String title,
                                 String status,
                                 Long assigneeUserId,
                                 LocalDateTime dueTime) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(33L);
        when(resultSet.getString("title")).thenReturn(title);
        when(resultSet.getString("related_type")).thenReturn("CUSTOMER");
        when(resultSet.getObject("related_id")).thenReturn(66L);
        when(resultSet.getObject("assignee_user_id")).thenReturn(assigneeUserId);
        when(resultSet.getTimestamp("due_time")).thenReturn(Timestamp.valueOf(dueTime));
        when(resultSet.getTimestamp("remind_time")).thenReturn(Timestamp.valueOf(dueTime.minusMinutes(30)));
        when(resultSet.getString("remind_status")).thenReturn("PENDING");
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 17, 10)));
        return mapper.mapRow(resultSet, 0);
    }
}
