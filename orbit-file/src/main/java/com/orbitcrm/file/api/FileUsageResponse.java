package com.orbitcrm.file.api;

public class FileUsageResponse {
    private Long usedBytes;
    private Long quotaBytes;
    private Long remainingBytes;
    private Boolean unlimited;
    private Integer activeFileCount;

    public Long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Long getQuotaBytes() {
        return quotaBytes;
    }

    public void setQuotaBytes(Long quotaBytes) {
        this.quotaBytes = quotaBytes;
    }

    public Long getRemainingBytes() {
        return remainingBytes;
    }

    public void setRemainingBytes(Long remainingBytes) {
        this.remainingBytes = remainingBytes;
    }

    public Boolean getUnlimited() {
        return unlimited;
    }

    public void setUnlimited(Boolean unlimited) {
        this.unlimited = unlimited;
    }

    public Integer getActiveFileCount() {
        return activeFileCount;
    }

    public void setActiveFileCount(Integer activeFileCount) {
        this.activeFileCount = activeFileCount;
    }
}
