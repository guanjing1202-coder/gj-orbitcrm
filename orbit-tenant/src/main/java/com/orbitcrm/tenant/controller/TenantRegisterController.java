package com.orbitcrm.tenant.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.tenant.api.TenantRegisterRequest;
import com.orbitcrm.tenant.api.TenantRegisterResponse;
import com.orbitcrm.tenant.service.TenantProvisioningService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantRegisterController {
    private final TenantProvisioningService tenantProvisioningService;

    public TenantRegisterController(TenantProvisioningService tenantProvisioningService) {
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @PostMapping("/register")
    public ApiResult<TenantRegisterResponse> register(@Validated @RequestBody TenantRegisterRequest request) {
        return ApiResult.ok(tenantProvisioningService.register(request));
    }
}
