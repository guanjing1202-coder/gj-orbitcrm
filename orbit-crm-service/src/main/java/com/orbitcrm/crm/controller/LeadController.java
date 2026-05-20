package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.api.LeadAssignRequest;
import com.orbitcrm.crm.api.LeadCreateRequest;
import com.orbitcrm.crm.api.LeadResponse;
import com.orbitcrm.crm.api.LeadStatusUpdateRequest;
import com.orbitcrm.crm.api.LeadUpdateRequest;
import com.orbitcrm.crm.service.LeadService;
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
@RequestMapping("/api/crm/leads")
public class LeadController {
    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @GetMapping
    @RequiresPermission("crm:lead:manage")
    public ApiResult<List<LeadResponse>> listLeads(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResult.ok(leadService.listLeads(status));
    }

    @GetMapping("/deleted")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<List<LeadResponse>> listDeletedLeads() {
        return ApiResult.ok(leadService.listDeletedLeads());
    }

    @GetMapping("/{id}")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> getLead(@PathVariable("id") Long id) {
        return ApiResult.ok(leadService.getLead(id));
    }

    @PostMapping
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> createLead(@Validated @RequestBody LeadCreateRequest request) {
        return ApiResult.ok(leadService.createLead(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> updateLead(@PathVariable("id") Long id,
                                              @RequestBody LeadUpdateRequest request) {
        return ApiResult.ok(leadService.updateLead(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> updateStatus(@PathVariable("id") Long id,
                                                @Validated @RequestBody LeadStatusUpdateRequest request) {
        return ApiResult.ok(leadService.updateStatus(id, request.getStatus()));
    }

    @PatchMapping("/{id}/owner")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> assignLead(@PathVariable("id") Long id,
                                              @Validated @RequestBody LeadAssignRequest request) {
        return ApiResult.ok(leadService.assignLead(id, request.getOwnerUserId()));
    }

    @PostMapping("/{id}/convert")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<CustomerResponse> convertToCustomer(@PathVariable("id") Long id) {
        return ApiResult.ok(leadService.convertToCustomer(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> deleteLead(@PathVariable("id") Long id) {
        return ApiResult.ok(leadService.deleteLead(id));
    }

    @PatchMapping("/{id}/restore")
    @RequiresPermission("crm:lead:manage")
    public ApiResult<LeadResponse> restoreLead(@PathVariable("id") Long id) {
        return ApiResult.ok(leadService.restoreLead(id));
    }
}
