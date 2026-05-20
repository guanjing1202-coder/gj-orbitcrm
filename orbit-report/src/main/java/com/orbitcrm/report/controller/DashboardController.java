package com.orbitcrm.report.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.report.api.DashboardSummaryResponse;
import com.orbitcrm.report.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @RequiresPermission("report:dashboard:view")
    public ApiResult<DashboardSummaryResponse> summary() {
        return ApiResult.ok(dashboardService.summary());
    }
}
