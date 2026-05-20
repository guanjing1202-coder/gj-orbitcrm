package com.orbitcrm.crm.api;

import javax.validation.constraints.NotBlank;

public class LeadStatusUpdateRequest {
    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
