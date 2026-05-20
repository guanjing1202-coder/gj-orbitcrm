package com.orbitcrm.report.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.report.api.DashboardSummaryResponse;
import com.orbitcrm.report.api.DealFunnelStageResponse;
import com.orbitcrm.report.api.NameValueResponse;
import com.orbitcrm.report.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/lead-status")
    @RequiresPermission("report:dashboard:view")
    public ApiResult<List<NameValueResponse>> leadStatus() {
        return ApiResult.ok(dashboardService.leadStatus());
    }

    @GetMapping("/deal-status")
    @RequiresPermission("report:dashboard:view")
    public ApiResult<List<NameValueResponse>> dealStatus() {
        return ApiResult.ok(dashboardService.dealStatus());
    }

    @GetMapping("/task-status")
    @RequiresPermission("report:dashboard:view")
    public ApiResult<List<NameValueResponse>> taskStatus() {
        return ApiResult.ok(dashboardService.taskStatus());
    }

    @GetMapping("/deal-funnel")
    @RequiresPermission("report:dashboard:view")
    public ApiResult<List<DealFunnelStageResponse>> dealFunnel() {
        return ApiResult.ok(dashboardService.dealFunnel());
    }
}
