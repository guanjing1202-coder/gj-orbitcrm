package com.orbitcrm.admin.controller;

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

    @PatchMapping("/{id}/status")
    public ApiResult<PlatformTenantResponse> updateStatus(
            @PathVariable("id") Long id,
            @Validated @RequestBody PlatformTenantStatusRequest request) {
        return ApiResult.ok(platformAdminService.updateTenantStatus(id, request.getStatus()));
    }
}
