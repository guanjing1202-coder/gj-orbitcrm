package com.orbitcrm.common.datasource;

import com.orbitcrm.common.core.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantJdbcTemplateProvider {
    private final JdbcTemplate platformJdbcTemplate;
    private final Map<String, JdbcTemplate> tenantJdbcTemplates = new ConcurrentHashMap<String, JdbcTemplate>();

    public TenantJdbcTemplateProvider(JdbcTemplate platformJdbcTemplate) {
        this.platformJdbcTemplate = platformJdbcTemplate;
    }

    public JdbcTemplate currentTenantJdbcTemplate() {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        return tenantJdbcTemplates.computeIfAbsent(tenantCode, this::createTenantJdbcTemplate);
    }

    private JdbcTemplate createTenantJdbcTemplate(String tenantCode) {
        TenantDatabaseProperties properties = loadTenantDatabaseProperties(tenantCode);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(decodePasswordCipher(properties.getPassword()));
        return new JdbcTemplate(dataSource);
    }

    private TenantDatabaseProperties loadTenantDatabaseProperties(String tenantCode) {
        List<TenantDatabaseProperties> records = platformJdbcTemplate.query(
                "SELECT d.tenant_code, d.jdbc_url, d.username, d.password_cipher " +
                        "FROM platform_tenant_database d JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE d.tenant_code = ? AND d.status = 'ACTIVE' AND t.status = 'ACTIVE'",
                new Object[]{tenantCode},
                (rs, rowNum) -> {
                    TenantDatabaseProperties properties = new TenantDatabaseProperties();
                    properties.setTenantCode(rs.getString("tenant_code"));
                    properties.setJdbcUrl(rs.getString("jdbc_url"));
                    properties.setUsername(rs.getString("username"));
                    properties.setPassword(rs.getString("password_cipher"));
                    return properties;
                });
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant database is not available");
        }
        return records.get(0);
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
}
