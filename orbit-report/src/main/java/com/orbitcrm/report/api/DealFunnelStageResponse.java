package com.orbitcrm.report.api;

public class DealFunnelStageResponse {
    private Long stageId;
    private String stageName;
    private Integer winProbability;
    private Integer sortOrder;
    private Long dealCount;
    private Long totalAmountCent;

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

    public Long getDealCount() {
        return dealCount;
    }

    public void setDealCount(Long dealCount) {
        this.dealCount = dealCount;
    }

    public Long getTotalAmountCent() {
        return totalAmountCent;
    }

    public void setTotalAmountCent(Long totalAmountCent) {
        this.totalAmountCent = totalAmountCent;
    }
}
