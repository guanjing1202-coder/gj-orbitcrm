package com.orbitcrm.crm.api;

import java.util.List;

public class CustomerTagAssignRequest {
    private List<Long> tagIds;

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
