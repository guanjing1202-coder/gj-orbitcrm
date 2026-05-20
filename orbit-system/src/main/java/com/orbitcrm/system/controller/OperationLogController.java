package com.orbitcrm.system.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.system.api.OperationLogResponse;
import com.orbitcrm.system.service.OperationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/operation-logs")
public class OperationLogController {
    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    @RequiresPermission("system:audit:view")
    public ApiResult<List<OperationLogResponse>> listLogs(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(operationLogService.listLogs(action, limit));
    }
}
