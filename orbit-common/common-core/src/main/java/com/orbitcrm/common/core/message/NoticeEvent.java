package com.orbitcrm.common.core.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NoticeEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tenantCode;
    private String title;
    private String content;
    private String noticeType = "SYSTEM";
    private List<Long> receiverUserIds = new ArrayList<Long>();

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getNoticeType() {
        return noticeType;
    }

    public void setNoticeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public List<Long> getReceiverUserIds() {
        return receiverUserIds;
    }

    public void setReceiverUserIds(List<Long> receiverUserIds) {
        this.receiverUserIds = receiverUserIds;
    }
}
