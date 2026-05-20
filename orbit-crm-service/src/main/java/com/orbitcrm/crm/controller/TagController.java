package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.TagCreateRequest;
import com.orbitcrm.crm.api.TagResponse;
import com.orbitcrm.crm.service.TagService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @RequiresPermission("crm:tag:manage")
    public ApiResult<List<TagResponse>> listTags() {
        return ApiResult.ok(tagService.listTags());
    }

    @PostMapping
    @RequiresPermission("crm:tag:manage")
    public ApiResult<TagResponse> createTag(@Validated @RequestBody TagCreateRequest request) {
        return ApiResult.ok(tagService.createTag(request));
    }
}
