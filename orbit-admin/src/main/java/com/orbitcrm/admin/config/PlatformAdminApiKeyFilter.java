package com.orbitcrm.admin.config;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class PlatformAdminApiKeyFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Platform-Admin-Key";

    private final String apiKey;

    public PlatformAdminApiKeyFilter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String providedKey = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(apiKey) || "change-me-admin-key".equals(apiKey)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "platform admin api key is not configured");
            return;
        }
        if (!apiKey.equals(providedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid platform admin api key");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
