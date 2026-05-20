package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.CustomerCreateRequest;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.api.CustomerTransferRequest;
import com.orbitcrm.crm.api.CustomerUpdateRequest;
import com.orbitcrm.crm.service.CustomerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/crm/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @RequiresPermission("crm:customer:manage")
    public ApiResult<List<CustomerResponse>> listCustomers() {
        return ApiResult.ok(customerService.listCustomers());
    }

    @GetMapping("/deleted")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<List<CustomerResponse>> listDeletedCustomers() {
        return ApiResult.ok(customerService.listDeletedCustomers());
    }

    @GetMapping("/{id}")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> getCustomer(@PathVariable("id") Long id) {
        return ApiResult.ok(customerService.getCustomer(id));
    }

    @PostMapping
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> createCustomer(@Validated @RequestBody CustomerCreateRequest request) {
        return ApiResult.ok(customerService.createCustomer(request));
    }

    @PatchMapping("/{id}")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> updateCustomer(@PathVariable("id") Long id,
                                                      @RequestBody CustomerUpdateRequest request) {
        return ApiResult.ok(customerService.updateCustomer(id, request));
    }

    @PatchMapping("/{id}/owner")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> transferCustomer(@PathVariable("id") Long id,
                                                       @Validated @RequestBody CustomerTransferRequest request) {
        return ApiResult.ok(customerService.transferCustomer(id, request.getOwnerUserId()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> deleteCustomer(@PathVariable("id") Long id) {
        return ApiResult.ok(customerService.deleteCustomer(id));
    }

    @PatchMapping("/{id}/restore")
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> restoreCustomer(@PathVariable("id") Long id) {
        return ApiResult.ok(customerService.restoreCustomer(id));
    }
}
