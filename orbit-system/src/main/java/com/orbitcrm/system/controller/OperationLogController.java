package com.orbitcrm.system.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.system.api.OperationLogResponse;
import com.orbitcrm.system.api.OperationLogStatResponse;
import com.orbitcrm.system.service.OperationLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
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
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(operationLogService.listLogs(action, userId, targetType, targetId, startTime, endTime, limit));
    }

    @GetMapping("/action-stats")
    @RequiresPermission("system:audit:view")
    public ApiResult<List<OperationLogStatResponse>> actionStats(
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResult.ok(operationLogService.actionStats(startTime, endTime, limit));
    }
}
