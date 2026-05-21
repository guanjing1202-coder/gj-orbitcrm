package com.orbitcrm.system.api;

import java.time.LocalDateTime;

public class OperationLogStatResponse {
    private String action;
    private Long total;
    private LocalDateTime latestTime;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public LocalDateTime getLatestTime() {
        return latestTime;
    }

    public void setLatestTime(LocalDateTime latestTime) {
        this.latestTime = latestTime;
    }
}
