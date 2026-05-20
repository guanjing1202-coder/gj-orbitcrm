package com.orbitcrm.admin.controller;

import com.orbitcrm.admin.api.PlatformOperationLogResponse;
import com.orbitcrm.admin.service.PlatformAdminService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/operation-logs")
public class PlatformOperationLogController {
    private final PlatformAdminService platformAdminService;

    public PlatformOperationLogController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    public ApiResult<List<PlatformOperationLogResponse>> listLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(platformAdminService.listOperationLogs(action, limit));
    }
}
