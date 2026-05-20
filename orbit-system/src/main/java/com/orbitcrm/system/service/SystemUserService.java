package com.orbitcrm.system.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.system.api.UserCreateRequest;
import com.orbitcrm.system.api.UserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SystemUserService {
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SystemUserService(JdbcTemplate platformJdbcTemplate,
                             TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<UserResponse> listUsers() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT u.id, u.username, u.real_name, u.email, u.phone, u.status, u.create_time, " +
                        "GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS role_codes " +
                        "FROM sys_user u " +
                        "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
                        "LEFT JOIN sys_role r ON ur.role_id = r.id " +
                        "WHERE u.status <> 'DELETED' " +
                        "GROUP BY u.id, u.username, u.real_name, u.email, u.phone, u.status, u.create_time " +
                        "ORDER BY u.id DESC LIMIT 200",
                (rs, rowNum) -> {
                    UserResponse response = new UserResponse();
                    response.setId(rs.getLong("id"));
                    response.setUsername(rs.getString("username"));
                    response.setRealName(rs.getString("real_name"));
                    response.setEmail(rs.getString("email"));
                    response.setPhone(rs.getString("phone"));
                    response.setStatus(rs.getString("status"));
                    response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    response.setRoleCodes(splitCsv(rs.getString("role_codes")));
                    return response;
                });
    }

    @OperationLog(action = "USER_CREATE", targetType = "sys_user")
    public UserResponse createUser(UserCreateRequest request) {
        assertUserLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        try {
            jdbcTemplate.update(
                    "INSERT INTO sys_user (username, password_hash, real_name, email, phone, status) " +
                            "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                    request.getUsername(),
                    passwordEncoder.encode(request.getPassword()),
                    request.getRealName(),
                    request.getEmail(),
                    request.getPhone());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists", ex);
        }
        Long userId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        assignRoles(jdbcTemplate, userId, request.getRoleCodes());
        return getUser(userId);
    }

    @OperationLog(action = "USER_STATUS_UPDATE", targetType = "sys_user", targetIdArg = 0)
    public UserResponse updateStatus(Long id, String status) {
        int updated = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().update(
                "UPDATE sys_user SET status = ? WHERE id = ?",
                status,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
        return getUser(id);
    }

    private UserResponse getUser(Long id) {
        List<UserResponse> users = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT u.id, u.username, u.real_name, u.email, u.phone, u.status, u.create_time, " +
                        "GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS role_codes " +
                        "FROM sys_user u " +
                        "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
                        "LEFT JOIN sys_role r ON ur.role_id = r.id " +
                        "WHERE u.id = ? " +
                        "GROUP BY u.id, u.username, u.real_name, u.email, u.phone, u.status, u.create_time",
                new Object[]{id},
                (rs, rowNum) -> {
                    UserResponse response = new UserResponse();
                    response.setId(rs.getLong("id"));
                    response.setUsername(rs.getString("username"));
                    response.setRealName(rs.getString("real_name"));
                    response.setEmail(rs.getString("email"));
                    response.setPhone(rs.getString("phone"));
                    response.setStatus(rs.getString("status"));
                    response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    response.setRoleCodes(splitCsv(rs.getString("role_codes")));
                    return response;
                });
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
        return users.get(0);
    }

    private void assignRoles(JdbcTemplate jdbcTemplate, Long userId, List<String> roleCodes) {
        List<String> resolvedRoleCodes = roleCodes == null || roleCodes.isEmpty()
                ? Arrays.asList("sales")
                : roleCodes;
        for (String roleCode : resolvedRoleCodes) {
            Long roleId = roleId(jdbcTemplate, roleCode);
            jdbcTemplate.update(
                    "INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE role_id = VALUES(role_id)",
                    userId,
                    roleId);
        }
    }

    private Long roleId(JdbcTemplate jdbcTemplate, String roleCode) {
        List<Long> roleIds = jdbcTemplate.query(
                "SELECT id FROM sys_role WHERE role_code = ?",
                new Object[]{roleCode},
                (rs, rowNum) -> rs.getLong("id"));
        if (roleIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role not found: " + roleCode);
        }
        return roleIds.get(0);
    }

    private void assertUserLimitAvailable() {
        int limit = currentLimit("max_users");
        if (limit < 0) {
            return;
        }
        Integer used = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE status <> 'DELETED'",
                Integer.class);
        if (used != null && used >= limit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "user limit exceeded");
        }
    }

    private int currentLimit(String featureKey) {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        List<String> values = platformJdbcTemplate.query(
                "SELECT f.feature_value FROM platform_tenant t " +
                        "JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "JOIN platform_plan_feature f ON s.plan_id = f.plan_id " +
                        "WHERE t.tenant_code = ? AND f.feature_key = ? ORDER BY s.id DESC LIMIT 1",
                new Object[]{tenantCode, featureKey},
                (rs, rowNum) -> rs.getString("feature_value"));
        if (values.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(values.get(0));
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return new ArrayList<String>();
        }
        return Arrays.asList(csv.split(","));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
