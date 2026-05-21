package com.orbitcrm.admin.controller;

import com.orbitcrm.admin.api.PlatformOrderResponse;
import com.orbitcrm.admin.api.PlatformSubscriptionResponse;
import com.orbitcrm.admin.api.PlatformTenantResponse;
import com.orbitcrm.admin.api.PlatformTenantStatusRequest;
import com.orbitcrm.admin.service.PlatformAdminService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tenants")
public class PlatformTenantController {
    private final PlatformAdminService platformAdminService;

    public PlatformTenantController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    public ApiResult<List<PlatformTenantResponse>> listTenants(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResult.ok(platformAdminService.listTenants(status));
    }

    @GetMapping("/{id}")
    public ApiResult<PlatformTenantResponse> getTenant(@PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.getTenant(id));
    }

    @GetMapping("/{id}/subscriptions")
    public ApiResult<List<PlatformSubscriptionResponse>> listTenantSubscriptions(
            @PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.listTenantSubscriptions(id));
    }

    @GetMapping("/{id}/orders")
    public ApiResult<List<PlatformOrderResponse>> listTenantOrders(
            @PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.listTenantOrders(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResult<PlatformTenantResponse> updateStatus(
            @PathVariable("id") Long id,
            @Validated @RequestBody PlatformTenantStatusRequest request) {
        return ApiResult.ok(platformAdminService.updateTenantStatus(id, request.getStatus()));
    }
}
