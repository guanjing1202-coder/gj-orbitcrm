package com.orbitcrm.auth.api;

import javax.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank
    private String tenantCode;
    @NotBlank
    private String username;
    @NotBlank
    private String password;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
