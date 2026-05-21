package com.orbitcrm.billing.controller;

import com.orbitcrm.billing.api.BillingOrderCreateRequest;
import com.orbitcrm.billing.api.BillingOrderResponse;
import com.orbitcrm.billing.api.PaymentConfirmRequest;
import com.orbitcrm.billing.api.PaymentResponse;
import com.orbitcrm.billing.api.PlanResponse;
import com.orbitcrm.billing.api.SubscriptionResponse;
import com.orbitcrm.billing.service.BillingService;
import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.security.RequiresPermission;
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
@RequestMapping("/api/billing")
public class PlanController {
    private final BillingService billingService;

    public PlanController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public ApiResult<List<PlanResponse>> listPlans() {
        return ApiResult.ok(billingService.listPlans());
    }

    @GetMapping("/subscriptions/current")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<SubscriptionResponse> currentSubscription(
            @RequestParam(value = "tenantCode", required = false) String tenantCode) {
        String resolvedTenantCode = tenantCode == null ? TenantContext.getTenantCode() : tenantCode;
        return ApiResult.ok(billingService.currentSubscription(resolvedTenantCode));
    }

    @PatchMapping("/subscriptions/current/cancel")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<SubscriptionResponse> cancelCurrentSubscription(
            @RequestParam(value = "tenantCode", required = false) String tenantCode) {
        String resolvedTenantCode = tenantCode == null ? TenantContext.getTenantCode() : tenantCode;
        return ApiResult.ok(billingService.cancelCurrentSubscription(resolvedTenantCode));
    }

    @PatchMapping("/subscriptions/current/resume")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<SubscriptionResponse> resumeCurrentSubscription(
            @RequestParam(value = "tenantCode", required = false) String tenantCode) {
        String resolvedTenantCode = tenantCode == null ? TenantContext.getTenantCode() : tenantCode;
        return ApiResult.ok(billingService.resumeCurrentSubscription(resolvedTenantCode));
    }

    @GetMapping("/orders")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<List<BillingOrderResponse>> listOrders(
            @RequestParam(value = "tenantCode", required = false) String tenantCode) {
        String resolvedTenantCode = tenantCode == null ? TenantContext.getTenantCode() : tenantCode;
        return ApiResult.ok(billingService.listOrders(resolvedTenantCode));
    }

    @GetMapping("/orders/{id}")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<BillingOrderResponse> getOrder(@PathVariable("id") Long id) {
        return ApiResult.ok(billingService.getOrder(id));
    }

    @PostMapping("/orders")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<BillingOrderResponse> createOrder(
            @Validated @RequestBody BillingOrderCreateRequest request) {
        return ApiResult.ok(billingService.createOrder(request));
    }

    @PatchMapping("/orders/{id}/cancel")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<BillingOrderResponse> cancelOrder(@PathVariable("id") Long id) {
        return ApiResult.ok(billingService.cancelOrder(id));
    }

    @PostMapping("/orders/{id}/payments")
    @RequiresPermission("billing:subscription:manage")
    public ApiResult<PaymentResponse> confirmPayment(@PathVariable("id") Long id,
                                                     @Validated @RequestBody PaymentConfirmRequest request) {
        return ApiResult.ok(billingService.confirmPayment(id, request));
    }
}
