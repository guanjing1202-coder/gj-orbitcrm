package com.orbitcrm.admin.api;

import javax.validation.constraints.NotBlank;

public class PlatformPlanFeatureRequest {
    @NotBlank
    private String featureKey;
    @NotBlank
    private String featureValue;
    private String valueType = "NUMBER";

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
