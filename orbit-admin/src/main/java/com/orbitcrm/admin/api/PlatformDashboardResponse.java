package com.orbitcrm.admin.api;

public class PlatformDashboardResponse {
    private Integer tenantCount;
    private Integer activeTenantCount;
    private Integer trialSubscriptionCount;
    private Integer activeSubscriptionCount;
    private Integer pastDueSubscriptionCount;
    private Integer frozenSubscriptionCount;
    private Long monthlyRecurringRevenueCent;
    private Long currentMonthPaidAmountCent;

    public Integer getTenantCount() {
        return tenantCount;
    }

    public void setTenantCount(Integer tenantCount) {
        this.tenantCount = tenantCount;
    }

    public Integer getActiveTenantCount() {
        return activeTenantCount;
    }

    public void setActiveTenantCount(Integer activeTenantCount) {
        this.activeTenantCount = activeTenantCount;
    }

    public Integer getTrialSubscriptionCount() {
        return trialSubscriptionCount;
    }

    public void setTrialSubscriptionCount(Integer trialSubscriptionCount) {
        this.trialSubscriptionCount = trialSubscriptionCount;
    }

    public Integer getActiveSubscriptionCount() {
        return activeSubscriptionCount;
    }

    public void setActiveSubscriptionCount(Integer activeSubscriptionCount) {
        this.activeSubscriptionCount = activeSubscriptionCount;
    }

    public Integer getPastDueSubscriptionCount() {
        return pastDueSubscriptionCount;
    }

    public void setPastDueSubscriptionCount(Integer pastDueSubscriptionCount) {
        this.pastDueSubscriptionCount = pastDueSubscriptionCount;
    }

    public Integer getFrozenSubscriptionCount() {
        return frozenSubscriptionCount;
    }

    public void setFrozenSubscriptionCount(Integer frozenSubscriptionCount) {
        this.frozenSubscriptionCount = frozenSubscriptionCount;
    }

    public Long getMonthlyRecurringRevenueCent() {
        return monthlyRecurringRevenueCent;
    }

    public void setMonthlyRecurringRevenueCent(Long monthlyRecurringRevenueCent) {
        this.monthlyRecurringRevenueCent = monthlyRecurringRevenueCent;
    }

    public Long getCurrentMonthPaidAmountCent() {
        return currentMonthPaidAmountCent;
    }

    public void setCurrentMonthPaidAmountCent(Long currentMonthPaidAmountCent) {
        this.currentMonthPaidAmountCent = currentMonthPaidAmountCent;
    }
}
