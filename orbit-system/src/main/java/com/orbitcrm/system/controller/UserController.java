package com.orbitcrm.system.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.system.api.UserCreateRequest;
import com.orbitcrm.system.api.UserResponse;
import com.orbitcrm.system.api.UserStatusUpdateRequest;
import com.orbitcrm.system.service.SystemUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/users")
public class UserController {
    private final SystemUserService systemUserService;

    public UserController(SystemUserService systemUserService) {
        this.systemUserService = systemUserService;
    }

    @GetMapping
    @RequiresPermission("system:user:manage")
    public ApiResult<List<UserResponse>> listUsers() {
        return ApiResult.ok(systemUserService.listUsers());
    }

    @PostMapping
    @RequiresPermission("system:user:manage")
    public ApiResult<UserResponse> createUser(@Validated @RequestBody UserCreateRequest request) {
        return ApiResult.ok(systemUserService.createUser(request));
    }

    @PatchMapping("/{id}/status")
    @RequiresPermission("system:user:manage")
    public ApiResult<UserResponse> updateStatus(@PathVariable("id") Long id,
                                                @Validated @RequestBody UserStatusUpdateRequest request) {
        return ApiResult.ok(systemUserService.updateStatus(id, request.getStatus()));
    }
}
