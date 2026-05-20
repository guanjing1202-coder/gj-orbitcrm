package com.orbitcrm.tenant.api;

public class TenantRegisterResponse {
    private String tenantCode;
    private String databaseName;
    private String status;
    private Long tenantId;
    private Long subscriptionId;

    public TenantRegisterResponse() {
    }

    public TenantRegisterResponse(String tenantCode, String databaseName, String status) {
        this.tenantCode = tenantCode;
        this.databaseName = databaseName;
        this.status = status;
    }

    public TenantRegisterResponse(String tenantCode, String databaseName, String status,
                                  Long tenantId, Long subscriptionId) {
        this.tenantCode = tenantCode;
        this.databaseName = databaseName;
        this.status = status;
        this.tenantId = tenantId;
        this.subscriptionId = subscriptionId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }
}
