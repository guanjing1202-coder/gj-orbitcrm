package com.orbitcrm.system.api;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class RoleCreateRequest {
    @NotBlank
    private String roleCode;
    @NotBlank
    private String roleName;
    private List<String> permissionCodes = new ArrayList<String>();

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes;
    }
}
