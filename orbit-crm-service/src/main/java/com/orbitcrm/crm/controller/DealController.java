package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealMoveStageRequest;
import com.orbitcrm.crm.api.DealResponse;
import com.orbitcrm.crm.api.PipelineStageBoardResponse;
import com.orbitcrm.crm.service.DealService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/deals")
public class DealController {
    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @PostMapping
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> createDeal(@Validated @RequestBody DealCreateRequest request) {
        return ApiResult.ok(dealService.createDeal(request));
    }

    @GetMapping("/board")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<List<PipelineStageBoardResponse>> board(
            @RequestParam(value = "pipelineId", required = false) Long pipelineId) {
        return ApiResult.ok(dealService.board(pipelineId));
    }

    @PatchMapping("/{id}/stage")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> moveStage(@PathVariable("id") Long id,
                                             @Validated @RequestBody DealMoveStageRequest request) {
        return ApiResult.ok(dealService.moveStage(id, request.getStageId()));
    }
}
