package com.orbitcrm.crm.api;

import java.util.ArrayList;
import java.util.List;

public class PipelineStageBoardResponse {
    private Long stageId;
    private String stageName;
    private Integer winProbability;
    private Integer sortOrder;
    private Long totalAmountCent = 0L;
    private List<DealResponse> deals = new ArrayList<DealResponse>();

    public Long getStageId() {
        return stageId;
    }

    public void setStageId(Long stageId) {
        this.stageId = stageId;
    }

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

    public Long getTotalAmountCent() {
        return totalAmountCent;
    }

    public void setTotalAmountCent(Long totalAmountCent) {
        this.totalAmountCent = totalAmountCent;
    }

    public List<DealResponse> getDeals() {
        return deals;
    }

    public void setDeals(List<DealResponse> deals) {
        this.deals = deals;
    }
}
