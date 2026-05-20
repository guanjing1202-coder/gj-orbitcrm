package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.DealAssignRequest;
import com.orbitcrm.crm.api.DealCreateRequest;
import com.orbitcrm.crm.api.DealMoveStageRequest;
import com.orbitcrm.crm.api.DealResponse;
import com.orbitcrm.crm.api.DealUpdateRequest;
import com.orbitcrm.crm.api.PipelineStageBoardResponse;
import com.orbitcrm.crm.service.DealService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping
    @RequiresPermission("crm:deal:manage")
    public ApiResult<List<DealResponse>> listDeals(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "pipelineId", required = false) Long pipelineId,
            @RequestParam(value = "customerId", required = false) Long customerId,
            @RequestParam(value = "ownerUserId", required = false) Long ownerUserId) {
        return ApiResult.ok(dealService.listDeals(status, pipelineId, customerId, ownerUserId));
    }

    @GetMapping("/deleted")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<List<DealResponse>> listDeletedDeals() {
        return ApiResult.ok(dealService.listDeletedDeals());
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

    @GetMapping("/{id}")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> getDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.getDeal(id));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> updateDeal(@PathVariable("id") Long id,
                                              @RequestBody DealUpdateRequest request) {
        return ApiResult.ok(dealService.updateDeal(id, request));
    }

    @PatchMapping("/{id}/stage")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> moveStage(@PathVariable("id") Long id,
                                             @Validated @RequestBody DealMoveStageRequest request) {
        return ApiResult.ok(dealService.moveStage(id, request.getStageId()));
    }

    @PatchMapping("/{id}/owner")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> assignDeal(@PathVariable("id") Long id,
                                              @Validated @RequestBody DealAssignRequest request) {
        return ApiResult.ok(dealService.assignDeal(id, request.getOwnerUserId()));
    }

    @PatchMapping("/{id}/win")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> winDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.winDeal(id));
    }

    @PatchMapping("/{id}/lose")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> loseDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.loseDeal(id));
    }

    @PatchMapping("/{id}/reopen")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> reopenDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.reopenDeal(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> deleteDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.deleteDeal(id));
    }

    @PatchMapping("/{id}/restore")
    @RequiresPermission("crm:deal:manage")
    public ApiResult<DealResponse> restoreDeal(@PathVariable("id") Long id) {
        return ApiResult.ok(dealService.restoreDeal(id));
    }
}
