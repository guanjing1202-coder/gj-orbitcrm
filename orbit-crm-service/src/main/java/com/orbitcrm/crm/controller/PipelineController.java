package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.PipelineCreateRequest;
import com.orbitcrm.crm.api.PipelineResponse;
import com.orbitcrm.crm.api.PipelineStageCreateRequest;
import com.orbitcrm.crm.api.PipelineStageResponse;
import com.orbitcrm.crm.api.PipelineStageUpdateRequest;
import com.orbitcrm.crm.service.PipelineService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/pipelines")
public class PipelineController {
    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @GetMapping
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<List<PipelineResponse>> listPipelines() {
        return ApiResult.ok(pipelineService.listPipelines());
    }

    @PostMapping
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<PipelineResponse> createPipeline(@Validated @RequestBody PipelineCreateRequest request) {
        return ApiResult.ok(pipelineService.createPipeline(request));
    }

    @PatchMapping("/{id}/default")
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<PipelineResponse> setDefaultPipeline(@PathVariable("id") Long id) {
        return ApiResult.ok(pipelineService.setDefaultPipeline(id));
    }

    @GetMapping("/{pipelineId}/stages")
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<List<PipelineStageResponse>> listStages(@PathVariable("pipelineId") Long pipelineId) {
        return ApiResult.ok(pipelineService.listStages(pipelineId));
    }

    @PostMapping("/{pipelineId}/stages")
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<PipelineStageResponse> createStage(
            @PathVariable("pipelineId") Long pipelineId,
            @Validated @RequestBody PipelineStageCreateRequest request) {
        return ApiResult.ok(pipelineService.createStage(pipelineId, request));
    }

    @PatchMapping("/stages/{stageId}")
    @RequiresPermission("crm:pipeline:manage")
    public ApiResult<PipelineStageResponse> updateStage(
            @PathVariable("stageId") Long stageId,
            @Validated @RequestBody PipelineStageUpdateRequest request) {
        return ApiResult.ok(pipelineService.updateStage(stageId, request));
    }
}
