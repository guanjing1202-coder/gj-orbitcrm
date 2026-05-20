package com.orbitcrm.common.log;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
public class OperationLogAspect {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public OperationLogAspect(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    @Around("@annotation(operationLog)")
    public Object writeOperationLog(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        Object result = joinPoint.proceed();
        writeLogSafely(joinPoint, operationLog, result);
        return result;
    }

    private void writeLogSafely(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result) {
        try {
            String tenantCode = TenantContext.getTenantCode();
            if (!StringUtils.hasText(tenantCode)) {
                return;
            }
            CurrentUser currentUser = CurrentUserContext.get();
            Long userId = currentUser == null ? null : currentUser.getUserId();
            tenantJdbcTemplateProvider.currentTenantJdbcTemplate().update(
                    "INSERT INTO sys_operation_log (user_id, action, target_type, target_id, detail_json) VALUES (?, ?, ?, ?, ?)",
                    userId,
                    operationLog.action(),
                    operationLog.targetType(),
                    resolveTargetId(joinPoint, operationLog, result),
                    detailJson(joinPoint));
        } catch (Exception ignored) {
            // Business operations must not fail because audit logging failed.
        }
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint, OperationLog operationLog, Object result) {
        if (operationLog.targetIdArg() >= 0 && joinPoint.getArgs().length > operationLog.targetIdArg()) {
            Object arg = joinPoint.getArgs()[operationLog.targetIdArg()];
            return arg == null ? null : String.valueOf(arg);
        }
        if (result == null) {
            return null;
        }
        try {
            Method method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            return id == null ? null : String.valueOf(id);
        } catch (Exception ex) {
            return null;
        }
    }

    private String detailJson(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        return "{\"method\":\"" + escape(methodName) + "\",\"ip\":\"" + escape(clientIp()) + "\"}";
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return "";
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
