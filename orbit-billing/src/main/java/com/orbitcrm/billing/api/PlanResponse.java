package com.orbitcrm.billing.api;

import java.util.ArrayList;
import java.util.List;

public class PlanResponse {
    private Long id;
    private String planCode;
    private String planName;
    private String billingCycle;
    private Long priceCent;
    private String status;
    private List<PlanFeatureResponse> features = new ArrayList<PlanFeatureResponse>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public List<PlanFeatureResponse> getFeatures() {
        return features;
    }

    public void setFeatures(List<PlanFeatureResponse> features) {
        this.features = features;
    }
}
