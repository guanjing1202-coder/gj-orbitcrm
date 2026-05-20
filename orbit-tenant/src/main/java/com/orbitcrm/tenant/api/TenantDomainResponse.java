package com.orbitcrm.tenant.api;

import java.time.LocalDateTime;

public class TenantDomainResponse {
    private Long id;
    private String tenantCode;
    private String domain;
    private String verifyToken;
    private String cnameTarget;
    private String verifyStatus;
    private String sslStatus;
    private String status;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getCnameTarget() {
        return cnameTarget;
    }

    public void setCnameTarget(String cnameTarget) {
        this.cnameTarget = cnameTarget;
    }

    public String getVerifyStatus() {
        return verifyStatus;
    }

    public void setVerifyStatus(String verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public String getSslStatus() {
        return sslStatus;
    }

    public void setSslStatus(String sslStatus) {
        this.sslStatus = sslStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
