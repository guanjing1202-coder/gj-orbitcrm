package com.orbitcrm.crm.api;

import javax.validation.constraints.NotBlank;

public class TagCreateRequest {
    @NotBlank
    private String tagName;
    private String color;

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
