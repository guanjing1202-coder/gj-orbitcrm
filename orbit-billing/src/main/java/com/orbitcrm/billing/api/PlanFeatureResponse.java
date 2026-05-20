package com.orbitcrm.billing.api;

public class PlanFeatureResponse {
    private String featureKey;
    private String featureValue;
    private String valueType;

    public PlanFeatureResponse() {
    }

    public PlanFeatureResponse(String featureKey, String featureValue, String valueType) {
        this.featureKey = featureKey;
        this.featureValue = featureValue;
        this.valueType = valueType;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public String getFeatureValue() {
        return featureValue;
    }

    public void setFeatureValue(String featureValue) {
        this.featureValue = featureValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }
}
