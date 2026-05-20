package com.orbitcrm.system.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.system.api.PermissionResponse;
import com.orbitcrm.system.api.RoleCreateRequest;
import com.orbitcrm.system.api.RoleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class SystemRoleService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public SystemRoleService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<RoleResponse> listRoles() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT r.id, r.role_code, r.role_name, " +
                        "GROUP_CONCAT(p.permission_code ORDER BY p.permission_code) AS permission_codes " +
                        "FROM sys_role r " +
                        "LEFT JOIN sys_role_permission rp ON r.id = rp.role_id " +
                        "LEFT JOIN sys_permission p ON rp.permission_id = p.id " +
                        "GROUP BY r.id, r.role_code, r.role_name ORDER BY r.id",
                (rs, rowNum) -> {
                    RoleResponse response = new RoleResponse();
                    response.setId(rs.getLong("id"));
                    response.setRoleCode(rs.getString("role_code"));
                    response.setRoleName(rs.getString("role_name"));
                    response.setPermissionCodes(splitCsv(rs.getString("permission_codes")));
                    return response;
                });
    }

    @OperationLog(action = "ROLE_UPSERT", targetType = "sys_role")
    public RoleResponse createRole(RoleCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO sys_role (role_code, role_name) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE role_name = VALUES(role_name)",
                request.getRoleCode(),
                request.getRoleName());
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?",
                new Object[]{request.getRoleCode()},
                Long.class);
        syncRolePermissions(jdbcTemplate, roleId, request.getPermissionCodes());
        return getRole(roleId);
    }

    public List<PermissionResponse> listPermissions() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, permission_code, permission_name, resource_type FROM sys_permission ORDER BY permission_code",
                (rs, rowNum) -> {
                    PermissionResponse response = new PermissionResponse();
                    response.setId(rs.getLong("id"));
                    response.setPermissionCode(rs.getString("permission_code"));
                    response.setPermissionName(rs.getString("permission_name"));
                    response.setResourceType(rs.getString("resource_type"));
                    return response;
                });
    }

    private RoleResponse getRole(Long roleId) {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT r.id, r.role_code, r.role_name, " +
                        "GROUP_CONCAT(p.permission_code ORDER BY p.permission_code) AS permission_codes " +
                        "FROM sys_role r " +
                        "LEFT JOIN sys_role_permission rp ON r.id = rp.role_id " +
                        "LEFT JOIN sys_permission p ON rp.permission_id = p.id " +
                        "WHERE r.id = ? GROUP BY r.id, r.role_code, r.role_name",
                new Object[]{roleId},
                (rs, rowNum) -> {
                    RoleResponse response = new RoleResponse();
                    response.setId(rs.getLong("id"));
                    response.setRoleCode(rs.getString("role_code"));
                    response.setRoleName(rs.getString("role_name"));
                    response.setPermissionCodes(splitCsv(rs.getString("permission_codes")));
                    return response;
                });
    }

    private void syncRolePermissions(JdbcTemplate jdbcTemplate, Long roleId, List<String> permissionCodes) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        for (String permissionCode : permissionCodes) {
            List<Long> permissionIds = jdbcTemplate.query(
                    "SELECT id FROM sys_permission WHERE permission_code = ?",
                    new Object[]{permissionCode},
                    (rs, rowNum) -> rs.getLong("id"));
            if (!permissionIds.isEmpty()) {
                jdbcTemplate.update(
                        "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE permission_id = VALUES(permission_id)",
                        roleId,
                        permissionIds.get(0));
            }
        }
    }

    private List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return new ArrayList<String>();
        }
        return Arrays.asList(csv.split(","));
    }
}
