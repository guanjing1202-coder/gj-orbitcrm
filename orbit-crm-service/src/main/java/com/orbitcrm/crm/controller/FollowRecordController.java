package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.FollowRecordCreateRequest;
import com.orbitcrm.crm.api.FollowRecordResponse;
import com.orbitcrm.crm.service.FollowRecordService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/follow-records")
public class FollowRecordController {
    private final FollowRecordService followRecordService;

    public FollowRecordController(FollowRecordService followRecordService) {
        this.followRecordService = followRecordService;
    }

    @GetMapping
    @RequiresPermission("crm:follow:manage")
    public ApiResult<List<FollowRecordResponse>> listFollowRecords(
            @RequestParam("relatedType") String relatedType,
            @RequestParam("relatedId") Long relatedId) {
        return ApiResult.ok(followRecordService.listFollowRecords(relatedType, relatedId));
    }

    @PostMapping
    @RequiresPermission("crm:follow:manage")
    public ApiResult<FollowRecordResponse> createFollowRecord(
            @Validated @RequestBody FollowRecordCreateRequest request) {
        return ApiResult.ok(followRecordService.createFollowRecord(request));
    }
}
