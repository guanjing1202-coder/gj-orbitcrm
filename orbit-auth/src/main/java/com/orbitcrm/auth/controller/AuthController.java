package com.orbitcrm.auth.controller;

import com.orbitcrm.auth.api.LoginRequest;
import com.orbitcrm.auth.api.LoginResponse;
import com.orbitcrm.auth.service.LoginService;
import com.orbitcrm.common.core.api.ApiResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Validated @RequestBody LoginRequest request,
                                          HttpServletRequest servletRequest) {
        return ApiResult.ok(loginService.login(
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && forwarded.trim().length() > 0) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
