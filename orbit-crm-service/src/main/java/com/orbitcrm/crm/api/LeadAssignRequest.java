package com.orbitcrm.crm.api;

import javax.validation.constraints.NotNull;

public class LeadAssignRequest {
    @NotNull
    private Long ownerUserId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }
}
