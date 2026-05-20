package com.orbitcrm.task.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.task.api.TaskCreateRequest;
import com.orbitcrm.task.api.TaskReminderDispatchResponse;
import com.orbitcrm.task.api.TaskResponse;
import com.orbitcrm.task.service.TaskReminderService;
import com.orbitcrm.task.service.TaskService;
import org.springframework.validation.annotation.Validated;
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
            @RequestParam(value = "assigneeUserId", required = false) Long assigneeUserId) {
        return ApiResult.ok(taskService.listTasks(status, assigneeUserId));
    }

    @PostMapping
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> createTask(@Validated @RequestBody TaskCreateRequest request) {
        return ApiResult.ok(taskService.createTask(request));
    }

    @PatchMapping("/{id}/complete")
    @RequiresPermission("task:manage")
    public ApiResult<TaskResponse> completeTask(@PathVariable("id") Long id) {
        return ApiResult.ok(taskService.completeTask(id));
    }

    @PostMapping("/reminders/dispatch")
    @RequiresPermission("task:manage")
    public ApiResult<TaskReminderDispatchResponse> dispatchReminders() {
        return ApiResult.ok(new TaskReminderDispatchResponse(taskReminderService.dispatchDueReminders()));
    }
}
