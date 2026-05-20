package com.orbitcrm.openapi.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.openapi.api.OpenApiKeyCreateRequest;
import com.orbitcrm.openapi.api.OpenApiKeyResponse;
import com.orbitcrm.openapi.service.OpenApiKeyService;
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
@RequestMapping("/api/openapi/keys")
public class OpenApiKeyController {
    private final OpenApiKeyService openApiKeyService;

    public OpenApiKeyController(OpenApiKeyService openApiKeyService) {
        this.openApiKeyService = openApiKeyService;
    }

    @GetMapping
    @RequiresPermission("openapi:key:manage")
    public ApiResult<List<OpenApiKeyResponse>> listKeys() {
        return ApiResult.ok(openApiKeyService.listKeys());
    }

    @PostMapping
    @RequiresPermission("openapi:key:manage")
    public ApiResult<OpenApiKeyResponse> createKey(@Validated @RequestBody OpenApiKeyCreateRequest request) {
        return ApiResult.ok(openApiKeyService.createKey(request));
    }

    @PatchMapping("/{id}/disable")
    @RequiresPermission("openapi:key:manage")
    public ApiResult<OpenApiKeyResponse> disableKey(@PathVariable("id") Long id) {
        return ApiResult.ok(openApiKeyService.disableKey(id));
    }
}
