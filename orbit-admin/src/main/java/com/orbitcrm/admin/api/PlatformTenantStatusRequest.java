package com.orbitcrm.admin.api;

import javax.validation.constraints.NotBlank;

public class PlatformTenantStatusRequest {
    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
