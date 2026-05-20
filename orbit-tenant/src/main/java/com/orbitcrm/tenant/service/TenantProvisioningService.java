package com.orbitcrm.tenant.service;

import com.orbitcrm.tenant.api.TenantRegisterRequest;
import com.orbitcrm.tenant.api.TenantRegisterResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TenantProvisioningService {
    private static final Pattern TENANT_CODE = Pattern.compile("^[a-z][a-z0-9-]{2,31}$");
    private static final Pattern DATABASE_NAME = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final String TEMPLATE_SQL = "database/tenant-template/001_tenant_schema.sql";

    private final JdbcTemplate platformJdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final int trialDays;

    public TenantProvisioningService(JdbcTemplate platformJdbcTemplate,
                                     @Value("${orbit.provisioning.mysql-host}") String mysqlHost,
                                     @Value("${orbit.provisioning.mysql-port}") int mysqlPort,
                                     @Value("${orbit.provisioning.mysql-username}") String mysqlUsername,
                                     @Value("${orbit.provisioning.mysql-password}") String mysqlPassword,
                                     @Value("${orbit.provisioning.trial-days}") int trialDays) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.trialDays = trialDays;
    }

    public TenantRegisterResponse register(TenantRegisterRequest request) {
        String tenantCode = normalizeTenantCode(request.getTenantCode());
        String planCode = normalizePlanCode(request.getPlanCode());
        String databaseName = "orbit_tenant_" + tenantCode.replace("-", "_");
        assertSafeDatabaseName(databaseName);

        Long tenantId = createTenantRow(tenantCode, request);
        try {
            createTenantDatabase(databaseName);
            initializeTenantDatabase(databaseName, request);
            createTenantDatabaseRow(tenantId, tenantCode, databaseName);
            Long subscriptionId = createTrialSubscription(tenantId, planCode);
            markTenantStatus(tenantId, "ACTIVE");
            writeOperationLog("TENANT_PROVISIONED", "platform_tenant", String.valueOf(tenantId),
                    "{\"tenantCode\":\"" + tenantCode + "\",\"databaseName\":\"" + databaseName + "\"}");
            return new TenantRegisterResponse(tenantCode, databaseName, "ACTIVE", tenantId, subscriptionId);
        } catch (RuntimeException ex) {
            markTenantStatus(tenantId, "FAILED");
            writeOperationLog("TENANT_PROVISION_FAILED", "platform_tenant", String.valueOf(tenantId),
                    "{\"tenantCode\":\"" + tenantCode + "\",\"message\":\"" + sanitizeJson(ex.getMessage()) + "\"}");
            throw ex;
        }
    }

    private Long createTenantRow(String tenantCode, TenantRegisterRequest request) {
        Integer exists = platformJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM platform_tenant WHERE tenant_code = ?",
                Integer.class,
                tenantCode);
        if (exists != null && exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tenant already exists");
        }
        try {
            platformJdbcTemplate.update(
                    "INSERT INTO platform_tenant (tenant_code, tenant_name, status, contact_name, contact_email) " +
                            "VALUES (?, ?, 'PROVISIONING', ?, ?)",
                    tenantCode,
                    request.getTenantName(),
                    request.getAdminUsername(),
                    request.getAdminEmail());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "tenant already exists", ex);
        }
        return platformJdbcTemplate.queryForObject(
                "SELECT id FROM platform_tenant WHERE tenant_code = ?",
                Long.class,
                tenantCode);
    }

    private void createTenantDatabase(String databaseName) {
        platformJdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS `" + databaseName +
                "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    }

    private void initializeTenantDatabase(String databaseName, TenantRegisterRequest request) {
        JdbcTemplate tenantJdbcTemplate = tenantJdbcTemplate(databaseName);
        executeTemplateSql(tenantJdbcTemplate);
        Long adminRoleId = tenantJdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'tenant_admin'",
                Long.class);
        tenantJdbcTemplate.update(
                "INSERT INTO sys_user (username, password_hash, real_name, email, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                request.getAdminUsername(),
                passwordEncoder.encode(request.getAdminPassword()),
                request.getAdminUsername(),
                request.getAdminEmail());
        Long adminUserId = tenantJdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                request.getAdminUsername());
        tenantJdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)",
                adminUserId,
                adminRoleId);
    }

    private void executeTemplateSql(JdbcTemplate tenantJdbcTemplate) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_SQL);
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (StringUtils.hasText(trimmed)) {
                    tenantJdbcTemplate.execute(trimmed);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to execute tenant template sql", ex);
        }
    }

    private void createTenantDatabaseRow(Long tenantId, String tenantCode, String databaseName) {
        String jdbcUrl = tenantJdbcUrl(databaseName);
        platformJdbcTemplate.update(
                "INSERT INTO platform_tenant_database (tenant_id, tenant_code, db_name, jdbc_url, username, password_cipher, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE') " +
                        "ON DUPLICATE KEY UPDATE db_name = VALUES(db_name), jdbc_url = VALUES(jdbc_url), " +
                        "username = VALUES(username), password_cipher = VALUES(password_cipher), status = 'ACTIVE'",
                tenantId,
                tenantCode,
                databaseName,
                jdbcUrl,
                mysqlUsername,
                encodePasswordCipher(mysqlPassword));
    }

    private Long createTrialSubscription(Long tenantId, String planCode) {
        Long planId = findPlanId(planCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trialEndTime = now.plusDays(trialDays);
        platformJdbcTemplate.update(
                "INSERT INTO platform_subscription (tenant_id, plan_id, status, start_time, end_time, trial_end_time, grace_days) " +
                        "VALUES (?, ?, 'TRIAL', ?, ?, ?, 7)",
                tenantId,
                planId,
                now,
                trialEndTime,
                trialEndTime);
        return platformJdbcTemplate.queryForObject(
                "SELECT id FROM platform_subscription WHERE tenant_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                tenantId);
    }

    private Long findPlanId(String planCode) {
        List<Long> planIds = platformJdbcTemplate.query(
                "SELECT id FROM platform_plan WHERE plan_code = ? AND status = 'ACTIVE'",
                new Object[]{planCode},
                (rs, rowNum) -> rs.getLong("id"));
        if (planIds.isEmpty() && !"starter".equals(planCode)) {
            return findPlanId("starter");
        }
        if (planIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active plan not found");
        }
        return planIds.get(0);
    }

    private void markTenantStatus(Long tenantId, String status) {
        platformJdbcTemplate.update(
                "UPDATE platform_tenant SET status = ? WHERE id = ?",
                status,
                tenantId);
    }

    private void writeOperationLog(String action, String targetType, String targetId, String detailJson) {
        platformJdbcTemplate.update(
                "INSERT INTO platform_operation_log (action, target_type, target_id, detail_json) VALUES (?, ?, ?, ?)",
                action,
                targetType,
                targetId,
                detailJson);
    }

    private JdbcTemplate tenantJdbcTemplate(String databaseName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(tenantJdbcUrl(databaseName));
        dataSource.setUsername(mysqlUsername);
        dataSource.setPassword(mysqlPassword);
        return new JdbcTemplate(dataSource);
    }

    private String tenantJdbcUrl(String databaseName) {
        return "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + databaseName +
                "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    }

    private String normalizeTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new IllegalArgumentException("tenantCode is required");
        }
        String normalized = tenantCode.trim().toLowerCase(Locale.ENGLISH);
        if (!TENANT_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("tenantCode must match ^[a-z][a-z0-9-]{2,31}$");
        }
        return normalized;
    }

    private String normalizePlanCode(String planCode) {
        if (!StringUtils.hasText(planCode)) {
            return "starter";
        }
        return planCode.trim().toLowerCase(Locale.ENGLISH);
    }

    private void assertSafeDatabaseName(String databaseName) {
        if (!DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("unsafe database name");
        }
    }

    private String encodePasswordCipher(String password) {
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    private String sanitizeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
