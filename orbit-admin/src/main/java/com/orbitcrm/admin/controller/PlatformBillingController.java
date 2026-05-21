package com.orbitcrm.admin.controller;

import com.orbitcrm.admin.api.PlatformOrderResponse;
import com.orbitcrm.admin.api.PlatformSubscriptionResponse;
import com.orbitcrm.admin.service.PlatformAdminService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/billing")
public class PlatformBillingController {
    private final PlatformAdminService platformAdminService;

    public PlatformBillingController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/subscriptions")
    public ApiResult<List<PlatformSubscriptionResponse>> listSubscriptions(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResult.ok(platformAdminService.listSubscriptions(status));
    }

    @GetMapping("/subscriptions/{id}")
    public ApiResult<PlatformSubscriptionResponse> getSubscription(@PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.getSubscription(id));
    }

    @GetMapping("/orders")
    public ApiResult<List<PlatformOrderResponse>> listOrders(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResult.ok(platformAdminService.listOrders(status));
    }

    @GetMapping("/orders/{id}")
    public ApiResult<PlatformOrderResponse> getOrder(@PathVariable("id") Long id) {
        return ApiResult.ok(platformAdminService.getOrder(id));
    }
}
