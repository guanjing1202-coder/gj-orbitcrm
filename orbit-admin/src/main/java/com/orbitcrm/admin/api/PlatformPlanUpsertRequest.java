package com.orbitcrm.admin.api;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class PlatformPlanUpsertRequest {
    @NotBlank
    private String planCode;
    @NotBlank
    private String planName;
    @NotBlank
    private String billingCycle;
    @NotNull
    private Long priceCent;
    private String status = "ACTIVE";
    private List<PlatformPlanFeatureRequest> features = new ArrayList<PlatformPlanFeatureRequest>();

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Long getPriceCent() {
        return priceCent;
    }

    public void setPriceCent(Long priceCent) {
        this.priceCent = priceCent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<PlatformPlanFeatureRequest> getFeatures() {
        return features;
    }

    public void setFeatures(List<PlatformPlanFeatureRequest> features) {
        this.features = features;
    }
}
