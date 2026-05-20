package com.orbitcrm.auth.service;

import com.orbitcrm.auth.api.LoginRequest;
import com.orbitcrm.auth.api.LoginResponse;
import com.orbitcrm.common.datasource.TenantDatabaseProperties;
import com.orbitcrm.common.datasource.TenantJdbcTemplateFactory;
import com.orbitcrm.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class LoginService {
    private static final Pattern TENANT_CODE = Pattern.compile("^[a-z][a-z0-9-]{2,31}$");

    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateFactory tenantJdbcTemplateFactory;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final long ttlMillis;

    public LoginService(JdbcTemplate platformJdbcTemplate,
                        TenantJdbcTemplateFactory tenantJdbcTemplateFactory,
                        JwtTokenProvider jwtTokenProvider,
                        @Value("${orbit.jwt.ttl-millis}") long ttlMillis) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateFactory = tenantJdbcTemplateFactory;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ttlMillis = ttlMillis;
    }

    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String tenantCode = normalizeTenantCode(request.getTenantCode());
        TenantDatabase tenantDatabase = loadTenantDatabase(tenantCode);
        assertSubscriptionAllowsLogin(tenantDatabase.getTenantId());

        JdbcTemplate tenantJdbcTemplate = tenantJdbcTemplateFactory.createTenantJdbcTemplate(
                tenantDatabase.getProperties());
        UserCredential credential = loadUser(tenantJdbcTemplate, request.getUsername());
        if (credential == null || !"ACTIVE".equals(credential.getStatus()) ||
                !passwordEncoder.matches(request.getPassword(), credential.getPasswordHash())) {
            writeLoginLog(tenantJdbcTemplate, credential == null ? null : credential.getId(),
                    request.getUsername(), "FAILED", ip, userAgent);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username or password");
        }

        writeLoginLog(tenantJdbcTemplate, credential.getId(), credential.getUsername(), "SUCCESS", ip, userAgent);
        String token = jwtTokenProvider.createToken(tenantCode, String.valueOf(credential.getId()), credential.getUsername());
        return new LoginResponse(token, ttlMillis / 1000, tenantCode, credential.getId(), credential.getUsername());
    }

    private TenantDatabase loadTenantDatabase(String tenantCode) {
        List<TenantDatabase> records = platformJdbcTemplate.query(
                "SELECT t.id AS tenant_id, t.status AS tenant_status, d.jdbc_url, d.username, d.password_cipher " +
                        "FROM platform_tenant t JOIN platform_tenant_database d ON t.id = d.tenant_id " +
                        "WHERE t.tenant_code = ? AND d.status = 'ACTIVE'",
                (rs, rowNum) -> new TenantDatabase(
                        rs.getLong("tenant_id"),
                        rs.getString("tenant_status"),
                        tenantCode,
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        rs.getString("password_cipher")),
                tenantCode);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tenant is not available");
        }
        TenantDatabase record = records.get(0);
        if (!"ACTIVE".equals(record.getTenantStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant is not active");
        }
        return record;
    }

    private void assertSubscriptionAllowsLogin(Long tenantId) {
        List<String> statuses = platformJdbcTemplate.query(
                "SELECT status FROM platform_subscription WHERE tenant_id = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("status"),
                tenantId);
        if (statuses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "subscription is not available");
        }
        String status = statuses.get(0);
        if ("FROZEN".equals(status) || "CANCELED".equals(status) || "EXPIRED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "subscription does not allow login");
        }
    }

    private UserCredential loadUser(JdbcTemplate tenantJdbcTemplate, String username) {
        List<UserCredential> users = tenantJdbcTemplate.query(
                "SELECT id, username, password_hash, status FROM sys_user WHERE username = ?",
                (rs, rowNum) -> new UserCredential(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("status")),
                username);
        return users.isEmpty() ? null : users.get(0);
    }

    private void writeLoginLog(JdbcTemplate tenantJdbcTemplate, Long userId, String username,
                               String result, String ip, String userAgent) {
        tenantJdbcTemplate.update(
                "INSERT INTO sys_login_log (user_id, username, login_result, ip, user_agent) VALUES (?, ?, ?, ?, ?)",
                userId,
                username,
                result,
                ip,
                userAgent);
    }

    private String normalizeTenantCode(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantCode is required");
        }
        String normalized = tenantCode.trim().toLowerCase(Locale.ENGLISH);
        if (!TENANT_CODE.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid tenantCode");
        }
        return normalized;
    }

    private static class TenantDatabase {
        private final Long tenantId;
        private final String tenantStatus;
        private final TenantDatabaseProperties properties;

        TenantDatabase(Long tenantId, String tenantStatus, String tenantCode,
                       String jdbcUrl, String username, String password) {
            this.tenantId = tenantId;
            this.tenantStatus = tenantStatus;
            this.properties = new TenantDatabaseProperties();
            this.properties.setTenantCode(tenantCode);
            this.properties.setJdbcUrl(jdbcUrl);
            this.properties.setUsername(username);
            this.properties.setPassword(password);
        }

        Long getTenantId() {
            return tenantId;
        }

        String getTenantStatus() {
            return tenantStatus;
        }

        TenantDatabaseProperties getProperties() {
            return properties;
        }
    }

    private static class UserCredential {
        private final Long id;
        private final String username;
        private final String passwordHash;
        private final String status;

        UserCredential(Long id, String username, String passwordHash, String status) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.status = status;
        }

        Long getId() {
            return id;
        }

        String getUsername() {
            return username;
        }

        String getPasswordHash() {
            return passwordHash;
        }

        String getStatus() {
            return status;
        }
    }
}
