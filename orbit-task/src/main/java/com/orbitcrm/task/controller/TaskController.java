package com.orbitcrm.task.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.task.api.TaskAssignRequest;
import com.orbitcrm.task.api.TaskCreateRequest;
import com.orbitcrm.task.api.TaskReminderDispatchResponse;
import com.orbitcrm.task.api.TaskResponse;
import com.orbitcrm.task.api.TaskUpdateRequest;
import com.orbitcrm.task.service.TaskReminderService;
import com.orbitcrm.task.service.TaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskReminderService taskReminderService;

    public TaskController(TaskService taskService,
                          TaskReminderService taskReminderService) {
        this.taskService = taskService;
        this.taskReminderService = taskReminderService;
    }

    @GetMapping
    @RequiresPermission("task:manage")
    public ApiResult<List<TaskResponse>> listTasks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "assigneeUserId", required = false) Long assigneeUserId,
            @RequestParam(value = "relatedType", required = false) String relatedType,
            @RequestParam(value = "relatedId", required = false) Long relatedId) {
        return ApiResult.ok(taskService.listTasks(status, assigneeUserId, relatedType, relatedId));
    }

    @GetMapping("/deleted")
    @RequiresPermission("task:manage")
    public ApiResult<List<TaskResponse>> listDeletedTasks() {
        return ApiResult.ok(taskService.listDeletedTasks());
    }

    @GetMapping("/{id}")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> getTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.getTask(id));
    }

    @PostMapping
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> createTask(@Validated @RequestBody TaskCreateRequest request) {
        return ApiResult.ok(taskService.createTask(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> updateTask(@PathVariable("id") Long id,
                                              @RequestBody TaskUpdateRequest request) {
        return ApiResult.ok(taskService.updateTask(id, request));
    }

    @PatchMapping("/{id}/assignee")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> assignTask(@PathVariable("id") Long id,
                                              @Validated @RequestBody TaskAssignRequest request) {
        return ApiResult.ok(taskService.assignTask(id, request.getAssigneeUserId()));
    }

    @PatchMapping("/{id}/complete")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> completeTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.completeTask(id));
    }

    @PatchMapping("/{id}/reopen")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> reopenTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.reopenTask(id));
    }

    @PatchMapping("/{id}/cancel")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> cancelTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.cancelTask(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> deleteTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.deleteTask(id));
    }

    @PatchMapping("/{id}/restore")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> restoreTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.restoreTask(id));
    }

    @PostMapping("/reminders/dispatch")
    @RequiresPermission("task:manage")
    public ApiResult<TaskReminderDispatchResponse> dispatchReminders() {
        return ApiResult.ok(new TaskReminderDispatchResponse(taskReminderService.dispatchDueReminders()));
    }
}
