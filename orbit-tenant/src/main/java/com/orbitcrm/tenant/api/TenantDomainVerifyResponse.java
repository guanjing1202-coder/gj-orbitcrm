package com.orbitcrm.tenant.api;

public class TenantDomainVerifyResponse {
    private Long id;
    private String domain;
    private String verifyStatus;
    private String status;
    private boolean cnameMatched;
    private boolean txtMatched;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getVerifyStatus() {
        return verifyStatus;
    }

    public void setVerifyStatus(String verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCnameMatched() {
        return cnameMatched;
    }

    public void setCnameMatched(boolean cnameMatched) {
        this.cnameMatched = cnameMatched;
    }

    public boolean isTxtMatched() {
        return txtMatched;
    }

    public void setTxtMatched(boolean txtMatched) {
        this.txtMatched = txtMatched;
    }
}
