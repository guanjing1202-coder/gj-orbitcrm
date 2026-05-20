package com.orbitcrm.common.security;

import com.orbitcrm.common.core.tenant.TenantConstants;
import com.orbitcrm.common.core.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtTokenProvider.parse(authorization.substring(7));
            String tenantCode = String.valueOf(claims.get(TenantConstants.TENANT_CLAIM));
            String currentTenant = TenantContext.getTenantCode();
            if (StringUtils.hasText(currentTenant) && !currentTenant.equals(tenantCode)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "tenant mismatch");
                return;
            }
            Long userId = Long.valueOf(claims.getSubject());
            String username = String.valueOf(claims.get("username"));
            CurrentUser currentUser = new CurrentUser(userId, username, tenantCode);
            TenantContext.setTenantCode(tenantCode);
            CurrentUserContext.set(currentUser);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid token");
        } finally {
            CurrentUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
