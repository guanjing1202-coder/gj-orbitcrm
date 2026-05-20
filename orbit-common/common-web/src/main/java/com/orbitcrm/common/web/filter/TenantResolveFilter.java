package com.orbitcrm.common.web.filter;

import com.orbitcrm.common.core.tenant.TenantConstants;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.core.tenant.TenantDomainResolver;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class TenantResolveFilter extends OncePerRequestFilter {
    private final String rootDomain;
    private final List<TenantDomainResolver> tenantDomainResolvers;

    public TenantResolveFilter(String rootDomain, List<TenantDomainResolver> tenantDomainResolvers) {
        this.rootDomain = rootDomain;
        this.tenantDomainResolvers = tenantDomainResolvers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String tenantCode = resolveTenantCode(request);
            if (StringUtils.hasText(tenantCode)) {
                TenantContext.setTenantCode(tenantCode);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantCode(HttpServletRequest request) {
        String host = normalizeHost(request.getServerName());
        String customDomainTenant = resolveCustomDomain(host);
        if (StringUtils.hasText(customDomainTenant)) {
            return customDomainTenant;
        }
        if (StringUtils.hasText(host) && StringUtils.hasText(rootDomain) && host.endsWith("." + rootDomain)) {
            String subdomain = host.substring(0, host.length() - rootDomain.length() - 1);
            if (StringUtils.hasText(subdomain) && !"www".equalsIgnoreCase(subdomain)) {
                return subdomain;
            }
        }
        return request.getHeader(TenantConstants.TENANT_HEADER);
    }

    private String resolveCustomDomain(String host) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        for (TenantDomainResolver resolver : tenantDomainResolvers) {
            String tenantCode = resolver.resolveTenantCode(host);
            if (StringUtils.hasText(tenantCode)) {
                return tenantCode;
            }
        }
        return null;
    }

    private String normalizeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ENGLISH);
    }
}
