package com.orbitcrm.crm.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.crm.api.CustomerCreateRequest;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.service.CustomerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping
    @RequiresPermission("crm:customer:manage")
    public ApiResult<CustomerResponse> createCustomer(@Validated @RequestBody CustomerCreateRequest request) {
        return ApiResult.ok(customerService.createCustomer(request));
    }
}
