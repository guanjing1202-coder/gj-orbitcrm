package com.orbitcrm.admin.controller;

import com.orbitcrm.admin.api.PlatformDashboardResponse;
import com.orbitcrm.admin.service.PlatformAdminService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class PlatformDashboardController {
    private final PlatformAdminService platformAdminService;

    public PlatformDashboardController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/summary")
    public ApiResult<PlatformDashboardResponse> summary() {
        return ApiResult.ok(platformAdminService.dashboard());
    }
}
