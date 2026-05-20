package com.orbitcrm.tenant.api;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

public class TenantRegisterRequest {
    @NotBlank
    private String tenantCode;
    @NotBlank
    private String tenantName;
    @NotBlank
    private String adminUsername;
    @Email
    @NotBlank
    private String adminEmail;
    @NotBlank
    private String adminPassword;
    private String planCode = "starter";

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }
}
