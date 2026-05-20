package com.orbitcrm.crm.api;

import javax.validation.constraints.NotNull;

public class DealAssignRequest {
    @NotNull
    private Long ownerUserId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }
}
