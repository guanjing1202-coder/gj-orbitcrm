package com.orbitcrm.task.api;

import javax.validation.constraints.NotNull;

public class TaskAssignRequest {
    @NotNull
    private Long assigneeUserId;

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }
}
