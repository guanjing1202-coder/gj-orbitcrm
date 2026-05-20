package com.orbitcrm.tenant.api;

import javax.validation.constraints.NotBlank;

public class TenantDomainBindRequest {
    @NotBlank
    private String domain;

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }
}
