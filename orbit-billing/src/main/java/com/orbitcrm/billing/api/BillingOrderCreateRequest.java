package com.orbitcrm.billing.api;

import javax.validation.constraints.NotBlank;

public class BillingOrderCreateRequest {
    private String tenantCode;
    @NotBlank
    private String planCode;
    private String orderType = "RENEW";
    private Integer periodMonths;

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Integer getPeriodMonths() {
        return periodMonths;
    }

    public void setPeriodMonths(Integer periodMonths) {
        this.periodMonths = periodMonths;
    }
}
