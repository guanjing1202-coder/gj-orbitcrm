package com.orbitcrm.common.datasource;

import com.orbitcrm.common.core.tenant.TenantContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TenantJdbcTemplateProvider implements DisposableBean {
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateFactory tenantJdbcTemplateFactory;
    private final Map<String, JdbcTemplate> tenantJdbcTemplates = new ConcurrentHashMap<String, JdbcTemplate>();

    public TenantJdbcTemplateProvider(JdbcTemplate platformJdbcTemplate,
                                      TenantJdbcTemplateFactory tenantJdbcTemplateFactory) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateFactory = tenantJdbcTemplateFactory;
    }

    public JdbcTemplate currentTenantJdbcTemplate() {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        return tenantJdbcTemplates.computeIfAbsent(tenantCode, this::createTenantJdbcTemplate);
    }

    public void refreshTenantJdbcTemplate(String tenantCode) {
        if (!StringUtils.hasText(tenantCode)) {
            return;
        }
        JdbcTemplate removed = tenantJdbcTemplates.remove(tenantCode);
        closeJdbcTemplateDataSource(removed);
    }

    @Override
    public void destroy() {
        for (JdbcTemplate jdbcTemplate : tenantJdbcTemplates.values()) {
            closeJdbcTemplateDataSource(jdbcTemplate);
        }
        tenantJdbcTemplates.clear();
    }

    private JdbcTemplate createTenantJdbcTemplate(String tenantCode) {
        TenantDatabaseProperties properties = loadTenantDatabaseProperties(tenantCode);
        return createTenantJdbcTemplate(properties);
    }

    JdbcTemplate createTenantJdbcTemplate(TenantDatabaseProperties properties) {
        return tenantJdbcTemplateFactory.createTenantJdbcTemplate(properties);
    }

    private TenantDatabaseProperties loadTenantDatabaseProperties(String tenantCode) {
        List<TenantDatabaseProperties> records = platformJdbcTemplate.query(
                "SELECT d.tenant_code, d.jdbc_url, d.username, d.password_cipher " +
                        "FROM platform_tenant_database d JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE d.tenant_code = ? AND d.status = 'ACTIVE' AND t.status = 'ACTIVE'",
                (rs, rowNum) -> {
                    TenantDatabaseProperties properties = new TenantDatabaseProperties();
                    properties.setTenantCode(rs.getString("tenant_code"));
                    properties.setJdbcUrl(rs.getString("jdbc_url"));
                    properties.setUsername(rs.getString("username"));
                    properties.setPassword(rs.getString("password_cipher"));
                    return properties;
                },
                tenantCode);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant database is not available");
        }
        return records.get(0);
    }

    private void closeJdbcTemplateDataSource(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            return;
        }
        DataSource dataSource = jdbcTemplate.getDataSource();
        try {
            if (dataSource instanceof Closeable) {
                ((Closeable) dataSource).close();
            } else if (dataSource instanceof AutoCloseable) {
                ((AutoCloseable) dataSource).close();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to close tenant datasource", ex);
        }
    }

}
