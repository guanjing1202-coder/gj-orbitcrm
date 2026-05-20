package com.orbitcrm.system.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.system.api.PermissionResponse;
import com.orbitcrm.system.api.RoleCreateRequest;
import com.orbitcrm.system.api.RoleResponse;
import com.orbitcrm.system.service.SystemRoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class RoleController {
    private final SystemRoleService systemRoleService;

    public RoleController(SystemRoleService systemRoleService) {
        this.systemRoleService = systemRoleService;
    }

    @GetMapping("/roles")
    @RequiresPermission("system:role:manage")
    public ApiResult<List<RoleResponse>> listRoles() {
        return ApiResult.ok(systemRoleService.listRoles());
    }

    @PostMapping("/roles")
    @RequiresPermission("system:role:manage")
    public ApiResult<RoleResponse> createRole(@Validated @RequestBody RoleCreateRequest request) {
        return ApiResult.ok(systemRoleService.createRole(request));
    }

    @GetMapping("/permissions")
    @RequiresPermission("system:role:manage")
    public ApiResult<List<PermissionResponse>> listPermissions() {
        return ApiResult.ok(systemRoleService.listPermissions());
    }
}
