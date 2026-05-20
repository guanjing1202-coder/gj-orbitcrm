package com.orbitcrm.report.api;

import java.util.ArrayList;
import java.util.List;

public class DashboardSummaryResponse {
    private Integer leadCount;
    private Integer customerCount;
    private Integer openDealCount;
    private Long openDealAmountCent;
    private Integer todayTaskCount;
    private Integer overdueTaskCount;
    private List<NameValueResponse> leadStatus = new ArrayList<NameValueResponse>();
    private List<NameValueResponse> dealFunnel = new ArrayList<NameValueResponse>();

    public Integer getLeadCount() {
        return leadCount;
    }

    public void setLeadCount(Integer leadCount) {
        this.leadCount = leadCount;
    }

    public Integer getCustomerCount() {
        return customerCount;
    }

    public void setCustomerCount(Integer customerCount) {
        this.customerCount = customerCount;
    }

    public Integer getOpenDealCount() {
        return openDealCount;
    }

    public void setOpenDealCount(Integer openDealCount) {
        this.openDealCount = openDealCount;
    }

    public Long getOpenDealAmountCent() {
        return openDealAmountCent;
    }

    public void setOpenDealAmountCent(Long openDealAmountCent) {
        this.openDealAmountCent = openDealAmountCent;
    }

    public Integer getTodayTaskCount() {
        return todayTaskCount;
    }

    public void setTodayTaskCount(Integer todayTaskCount) {
        this.todayTaskCount = todayTaskCount;
    }

    public Integer getOverdueTaskCount() {
        return overdueTaskCount;
    }

    public void setOverdueTaskCount(Integer overdueTaskCount) {
        this.overdueTaskCount = overdueTaskCount;
    }

    public List<NameValueResponse> getLeadStatus() {
        return leadStatus;
    }

    public void setLeadStatus(List<NameValueResponse> leadStatus) {
        this.leadStatus = leadStatus;
    }

    public List<NameValueResponse> getDealFunnel() {
        return dealFunnel;
    }

    public void setDealFunnel(List<NameValueResponse> dealFunnel) {
        this.dealFunnel = dealFunnel;
    }
}
