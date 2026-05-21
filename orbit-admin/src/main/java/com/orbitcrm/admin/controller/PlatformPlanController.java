package com.orbitcrm.admin.controller;

import com.orbitcrm.admin.api.PlatformPlanResponse;
import com.orbitcrm.admin.api.PlatformPlanUpsertRequest;
import com.orbitcrm.admin.service.PlatformAdminService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/plans")
public class PlatformPlanController {
    private final PlatformAdminService platformAdminService;

    public PlatformPlanController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    public ApiResult<List<PlatformPlanResponse>> listPlans() {
        return ApiResult.ok(platformAdminService.listPlans());
    }

    @GetMapping("/{id}")
    public ApiResult<PlatformPlanResponse> getPlan(@PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.getPlan(id));
    }

    @PostMapping
    public ApiResult<PlatformPlanResponse> upsertPlan(
            @Validated @RequestBody PlatformPlanUpsertRequest request) {
        return ApiResult.ok(platformAdminService.upsertPlan(request));
    }
}
