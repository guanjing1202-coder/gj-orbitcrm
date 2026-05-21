package com.orbitcrm.openapi.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.openapi.api.OpenApiKeyCreateRequest;
import com.orbitcrm.openapi.api.OpenApiKeyResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class OpenApiKeyService {
    private static final String DEFAULT_SCOPE = "crm:lead:write";
    private static final String KEY_PREFIX = "orb_";

    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final OpenApiKeyHasher keyHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpenApiKeyService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                             OpenApiKeyHasher keyHasher) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.keyHasher = keyHasher;
    }

    public List<OpenApiKeyResponse> listKeys() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, key_name, key_prefix, scopes, status, last_used_time, create_time " +
                        "FROM sys_openapi_key WHERE status <> 'DELETED' ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapKey(rs));
    }

    public OpenApiKeyResponse getKey(Long id) {
        return getKey(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    @OperationLog(action = "OPENAPI_KEY_CREATE", targetType = "sys_openapi_key")
    public OpenApiKeyResponse createKey(OpenApiKeyCreateRequest request) {
        String secretKey = generateSecretKey();
        String keyPrefix = secretKey.substring(0, Math.min(12, secretKey.length()));
        String scopes = normalizeScopes(request.getScopes());
        CurrentUser currentUser = CurrentUserContext.get();
        Long creatorUserId = currentUser == null ? null : currentUser.getUserId();

        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO sys_openapi_key (key_name, key_prefix, key_hash, scopes, creator_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                request.getKeyName(),
                keyPrefix,
                keyHasher.sha256(secretKey),
                scopes,
                creatorUserId);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        OpenApiKeyResponse response = getKey(jdbcTemplate, id);
        response.setSecretKey(secretKey);
        return response;
    }

    @OperationLog(action = "OPENAPI_KEY_ENABLE", targetType = "sys_openapi_key", targetIdArg = 0)
    public OpenApiKeyResponse enableKey(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'ACTIVE' WHERE id = ? AND status = 'DISABLED'",
                id);
        if (updated == 0) {
            ensureKeyExists(jdbcTemplate, id);
        }
        return getKey(jdbcTemplate, id);
    }

    @OperationLog(action = "OPENAPI_KEY_DISABLE", targetType = "sys_openapi_key", targetIdArg = 0)
    public OpenApiKeyResponse disableKey(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'DISABLED' WHERE id = ? AND status = 'ACTIVE'",
                id);
        if (updated == 0) {
            ensureKeyExists(jdbcTemplate, id);
        }
        return getKey(jdbcTemplate, id);
    }

    @OperationLog(action = "OPENAPI_KEY_ROTATE", targetType = "sys_openapi_key", targetIdArg = 0)
    public OpenApiKeyResponse rotateKey(Long id) {
        String secretKey = generateSecretKey();
        String keyPrefix = secretKey.substring(0, Math.min(12, secretKey.length()));
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_openapi_key SET key_prefix = ?, key_hash = ?, last_used_time = NULL " +
                        "WHERE id = ? AND status <> 'DELETED'",
                keyPrefix,
                keyHasher.sha256(secretKey),
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "openapi key not found");
        }
        OpenApiKeyResponse response = getKey(jdbcTemplate, id);
        response.setSecretKey(secretKey);
        return response;
    }

    @OperationLog(action = "OPENAPI_KEY_DELETE", targetType = "sys_openapi_key", targetIdArg = 0)
    public OpenApiKeyResponse deleteKey(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_openapi_key SET status = 'DELETED' WHERE id = ? AND status <> 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "openapi key not found");
        }
        return getKeyIncludingDeleted(jdbcTemplate, id);
    }

    private OpenApiKeyResponse getKey(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, key_name, key_prefix, scopes, status, last_used_time, create_time " +
                            "FROM sys_openapi_key WHERE id = ? AND status <> 'DELETED'",
                    (rs, rowNum) -> mapKey(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "openapi key not found", ex);
        }
    }

    private OpenApiKeyResponse getKeyIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, key_name, key_prefix, scopes, status, last_used_time, create_time " +
                            "FROM sys_openapi_key WHERE id = ?",
                    (rs, rowNum) -> mapKey(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "openapi key not found", ex);
        }
    }

    private void ensureKeyExists(JdbcTemplate jdbcTemplate, Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_openapi_key WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                id);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "openapi key not found");
        }
    }

    private String generateSecretKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeScopes(List<String> scopes) {
        List<String> normalized = new ArrayList<String>();
        if (scopes != null) {
            for (String scope : scopes) {
                if (StringUtils.hasText(scope)) {
                    normalized.add(scope.trim());
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(DEFAULT_SCOPE);
        }
        return StringUtils.collectionToCommaDelimitedString(normalized);
    }

    private OpenApiKeyResponse mapKey(ResultSet rs) throws SQLException {
        OpenApiKeyResponse response = new OpenApiKeyResponse();
        response.setId(rs.getLong("id"));
        response.setKeyName(rs.getString("key_name"));
        response.setKeyPrefix(rs.getString("key_prefix"));
        response.setScopes(parseScopes(rs.getString("scopes")));
        response.setStatus(rs.getString("status"));
        response.setLastUsedTime(toLocalDateTime(rs.getTimestamp("last_used_time")));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private List<String> parseScopes(String scopes) {
        List<String> result = new ArrayList<String>();
        if (!StringUtils.hasText(scopes)) {
            return result;
        }
        String[] parts = scopes.split(",");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
