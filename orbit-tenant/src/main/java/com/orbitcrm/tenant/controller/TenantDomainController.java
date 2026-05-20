package com.orbitcrm.tenant.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.tenant.api.TenantDomainBindRequest;
import com.orbitcrm.tenant.api.TenantDomainResponse;
import com.orbitcrm.tenant.api.TenantDomainVerifyResponse;
import com.orbitcrm.tenant.service.TenantDomainService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tenants/domains")
public class TenantDomainController {
    private final TenantDomainService tenantDomainService;

    public TenantDomainController(TenantDomainService tenantDomainService) {
        this.tenantDomainService = tenantDomainService;
    }

    @GetMapping
    @RequiresPermission("tenant:domain:manage")
    public ApiResult<List<TenantDomainResponse>> listDomains() {
        return ApiResult.ok(tenantDomainService.listDomains());
    }

    @PostMapping
    @RequiresPermission("tenant:domain:manage")
    public ApiResult<TenantDomainResponse> bindDomain(@Validated @RequestBody TenantDomainBindRequest request) {
        return ApiResult.ok(tenantDomainService.bindDomain(request));
    }

    @PostMapping("/{id}/verify")
    @RequiresPermission("tenant:domain:manage")
    public ApiResult<TenantDomainVerifyResponse> verifyDomain(@PathVariable("id") Long id) {
        return ApiResult.ok(tenantDomainService.verifyDomain(id));
    }

    @PatchMapping("/{id}/enable")
    @RequiresPermission("tenant:domain:manage")
    public ApiResult<TenantDomainResponse> enableDomain(@PathVariable("id") Long id) {
        return ApiResult.ok(tenantDomainService.enableDomain(id));
    }

    @PatchMapping("/{id}/disable")
    @RequiresPermission("tenant:domain:manage")
    public ApiResult<TenantDomainResponse> disableDomain(@PathVariable("id") Long id) {
        return ApiResult.ok(tenantDomainService.disableDomain(id));
    }
}
