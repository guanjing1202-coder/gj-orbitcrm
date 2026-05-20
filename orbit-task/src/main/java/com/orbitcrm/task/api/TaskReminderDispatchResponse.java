package com.orbitcrm.task.api;

public class TaskReminderDispatchResponse {
    private Integer dispatchedCount;

    public TaskReminderDispatchResponse() {
    }

    public TaskReminderDispatchResponse(Integer dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }

    public Integer getDispatchedCount() {
        return dispatchedCount;
    }

    public void setDispatchedCount(Integer dispatchedCount) {
        this.dispatchedCount = dispatchedCount;
    }
}
