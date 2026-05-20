package com.orbitcrm.crm.api;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

public class PipelineStageUpdateRequest {
    private String stageName;
    @Min(0)
    @Max(100)
    private Integer winProbability;
    private Integer sortOrder;

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public Integer getWinProbability() {
        return winProbability;
    }

    public void setWinProbability(Integer winProbability) {
        this.winProbability = winProbability;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
