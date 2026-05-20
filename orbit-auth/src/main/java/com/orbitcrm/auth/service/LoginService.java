package com.orbitcrm.auth.service;

import com.orbitcrm.auth.api.LoginRequest;
import com.orbitcrm.auth.api.LoginResponse;
import com.orbitcrm.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class LoginService {
    private static final Pattern TENANT_CODE = Pattern.compile("^[a-z][a-z0-9-]{2,31}$");

    private final JdbcTemplate platformJdbcTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final long ttlMillis;

    public LoginService(JdbcTemplate platformJdbcTemplate,
                        JwtTokenProvider jwtTokenProvider,
                        @Value("${orbit.jwt.ttl-millis}") long ttlMillis) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
        this.ttlMillis = ttlMillis;
    }

    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String tenantCode = normalizeTenantCode(request.getTenantCode());
        TenantDatabase tenantDatabase = loadTenantDatabase(tenantCode);
        assertSubscriptionAllowsLogin(tenantDatabase.getTenantId());

        JdbcTemplate tenantJdbcTemplate = tenantJdbcTemplate(tenantDatabase);
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
                new Object[]{tenantCode},
                (rs, rowNum) -> new TenantDatabase(
                        rs.getLong("tenant_id"),
                        rs.getString("tenant_status"),
                        rs.getString("jdbc_url"),
                        rs.getString("username"),
                        decodePasswordCipher(rs.getString("password_cipher"))));
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
                new Object[]{tenantId},
                (rs, rowNum) -> rs.getString("status"));
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
                new Object[]{username},
                (rs, rowNum) -> new UserCredential(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("status")));
        return users.isEmpty() ? null : users.get(0);
    }

    private JdbcTemplate tenantJdbcTemplate(TenantDatabase tenantDatabase) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(tenantDatabase.getJdbcUrl());
        dataSource.setUsername(tenantDatabase.getUsername());
        dataSource.setPassword(tenantDatabase.getPassword());
        return new JdbcTemplate(dataSource);
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

    private String decodePasswordCipher(String passwordCipher) {
        if (!StringUtils.hasText(passwordCipher)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(passwordCipher), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return passwordCipher;
        }
    }

    private static class TenantDatabase {
        private final Long tenantId;
        private final String tenantStatus;
        private final String jdbcUrl;
        private final String username;
        private final String password;

        TenantDatabase(Long tenantId, String tenantStatus, String jdbcUrl, String username, String password) {
            this.tenantId = tenantId;
            this.tenantStatus = tenantStatus;
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }

        Long getTenantId() {
            return tenantId;
        }

        String getTenantStatus() {
            return tenantStatus;
        }

        String getJdbcUrl() {
            return jdbcUrl;
        }

        String getUsername() {
            return username;
        }

        String getPassword() {
            return password;
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
