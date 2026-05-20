package com.orbitcrm.common.security;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
public class PermissionAspect {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public PermissionAspect(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    @Around("@annotation(requiresPermission)")
    public Object requirePermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        CurrentUser currentUser = CurrentUserContext.require();
        Integer count = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM sys_user u " +
                        "JOIN sys_user_role ur ON u.id = ur.user_id " +
                        "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
                        "JOIN sys_permission p ON rp.permission_id = p.id " +
                        "WHERE u.id = ? AND u.status = 'ACTIVE' AND p.permission_code = ?",
                new Object[]{currentUser.getUserId(), requiresPermission.value()},
                Integer.class);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "permission denied");
        }
        return joinPoint.proceed();
    }
}
