package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.CustomerTagAssignRequest;
import com.orbitcrm.crm.api.TagResponse;
import com.orbitcrm.crm.service.TagService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/customers/{customerId}/tags")
public class CustomerTagController {
    private final TagService tagService;

    public CustomerTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @RequiresPermission("crm:tag:manage")
    public ApiResult<List<TagResponse>> listCustomerTags(@PathVariable("customerId") Long customerId) {
        return ApiResult.ok(tagService.listCustomerTags(customerId));
    }

    @PutMapping
    @RequiresPermission("crm:tag:manage")
    public ApiResult<List<TagResponse>> replaceCustomerTags(
            @PathVariable("customerId") Long customerId,
            @RequestBody CustomerTagAssignRequest request) {
        return ApiResult.ok(tagService.replaceCustomerTags(customerId, request));
    }

    @PostMapping("/{tagId}")
    @RequiresPermission("crm:tag:manage")
    public ApiResult<List<TagResponse>> addCustomerTag(@PathVariable("customerId") Long customerId,
                                                       @PathVariable("tagId") Long tagId) {
        return ApiResult.ok(tagService.addCustomerTag(customerId, tagId));
    }

    @DeleteMapping("/{tagId}")
    @RequiresPermission("crm:tag:manage")
    public ApiResult<List<TagResponse>> removeCustomerTag(@PathVariable("customerId") Long customerId,
                                                          @PathVariable("tagId") Long tagId) {
        return ApiResult.ok(tagService.removeCustomerTag(customerId, tagId));
    }
}
