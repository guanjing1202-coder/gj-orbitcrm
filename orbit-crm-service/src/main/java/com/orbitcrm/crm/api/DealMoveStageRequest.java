package com.orbitcrm.crm.api;

import javax.validation.constraints.NotNull;

public class DealMoveStageRequest {
    @NotNull
    private Long stageId;

    public Long getStageId() {
        return stageId;
    }

    public void setStageId(Long stageId) {
        this.stageId = stageId;
    }
}
