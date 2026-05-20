package com.orbitcrm.common.security;

public class CurrentUser {
    private final Long userId;
    private final String username;
    private final String tenantCode;

    public CurrentUser(Long userId, String username, String tenantCode) {
        this.userId = userId;
        this.username = username;
        this.tenantCode = tenantCode;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getTenantCode() {
        return tenantCode;
    }
}
