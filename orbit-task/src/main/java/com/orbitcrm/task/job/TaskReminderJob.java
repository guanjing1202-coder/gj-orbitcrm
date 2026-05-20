package com.orbitcrm.task.job;

import com.orbitcrm.task.service.TaskReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskReminderJob {
    private final TaskReminderService taskReminderService;

    public TaskReminderJob(TaskReminderService taskReminderService) {
        this.taskReminderService = taskReminderService;
    }

    @Scheduled(cron = "${orbit.task.reminder-cron:0 */5 * * * ?}")
    public void dispatchDueReminders() {
        taskReminderService.dispatchDueReminders();
    }
}
