package com.orbitcrm.message.api;

public class UnreadCountResponse {
    private Integer unreadCount;

    public UnreadCountResponse() {
    }

    public UnreadCountResponse(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }
}
