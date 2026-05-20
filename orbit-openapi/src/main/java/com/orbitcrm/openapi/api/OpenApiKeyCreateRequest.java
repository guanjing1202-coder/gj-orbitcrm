package com.orbitcrm.openapi.api;

import javax.validation.constraints.NotBlank;
import java.util.List;

public class OpenApiKeyCreateRequest {
    @NotBlank
    private String keyName;
    private List<String> scopes;

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
}
