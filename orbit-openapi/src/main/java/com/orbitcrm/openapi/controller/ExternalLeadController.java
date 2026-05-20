package com.orbitcrm.openapi.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.openapi.api.OpenApiLeadCreateRequest;
import com.orbitcrm.openapi.api.OpenApiLeadResponse;
import com.orbitcrm.openapi.service.OpenApiKeyValidator;
import com.orbitcrm.openapi.service.OpenApiLeadService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/openapi/v1/leads")
public class ExternalLeadController {
    private final OpenApiKeyValidator openApiKeyValidator;
    private final OpenApiLeadService openApiLeadService;

    public ExternalLeadController(OpenApiKeyValidator openApiKeyValidator,
                                  OpenApiLeadService openApiLeadService) {
        this.openApiKeyValidator = openApiKeyValidator;
        this.openApiLeadService = openApiLeadService;
    }

    @PostMapping
    public ApiResult<OpenApiLeadResponse> createLead(HttpServletRequest request,
                                                     @Validated @RequestBody OpenApiLeadCreateRequest body) {
        openApiKeyValidator.requireScope(request, "crm:lead:write");
        return ApiResult.ok(openApiLeadService.createLead(body));
    }
}
