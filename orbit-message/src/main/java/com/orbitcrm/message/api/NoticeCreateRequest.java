package com.orbitcrm.message.api;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class NoticeCreateRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String noticeType = "SYSTEM";
    private List<Long> receiverUserIds = new ArrayList<Long>();

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
