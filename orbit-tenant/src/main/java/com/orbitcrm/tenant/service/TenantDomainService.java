package com.orbitcrm.tenant.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.tenant.api.TenantDomainBindRequest;
import com.orbitcrm.tenant.api.TenantDomainResponse;
import com.orbitcrm.tenant.api.TenantDomainVerifyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TenantDomainService {
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^(?=.{4,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private final JdbcTemplate platformJdbcTemplate;
    private final String cnameTarget;

    public TenantDomainService(JdbcTemplate platformJdbcTemplate,
                               @Value("${orbit.tenant.cname-target}") String cnameTarget) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.cnameTarget = normalizeDomain(cnameTarget);
    }

    public List<TenantDomainResponse> listDomains() {
        String tenantCode = requireTenantCode();
        return platformJdbcTemplate.query(
                "SELECT d.id, t.tenant_code, d.domain, d.verify_token, d.verify_status, d.ssl_status, d.status, d.create_time " +
                        "FROM platform_tenant_domain d JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE t.tenant_code = ? ORDER BY d.id DESC",
                (rs, rowNum) -> {
                    TenantDomainResponse response = new TenantDomainResponse();
                    response.setId(rs.getLong("id"));
                    response.setTenantCode(rs.getString("tenant_code"));
                    response.setDomain(rs.getString("domain"));
                    response.setVerifyToken(rs.getString("verify_token"));
                    response.setCnameTarget(cnameTarget);
                    response.setVerifyStatus(rs.getString("verify_status"));
                    response.setSslStatus(rs.getString("ssl_status"));
                    response.setStatus(rs.getString("status"));
                    response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    return response;
                },
                tenantCode
            );
    }

    @OperationLog(action = "TENANT_DOMAIN_BIND", targetType = "platform_tenant_domain")
    public TenantDomainResponse bindDomain(TenantDomainBindRequest request) {
        assertCustomDomainFeatureEnabled();
        String tenantCode = requireTenantCode();
        Long tenantId = tenantId(tenantCode);
        String domain = normalizeAndValidateDomain(request.getDomain());
        String verifyToken = "orbitcrm-domain-" + UUID.randomUUID().toString().replace("-", "");
        try {
            platformJdbcTemplate.update(
                    "INSERT INTO platform_tenant_domain (tenant_id, domain, verify_token, verify_status, ssl_status, status) " +
                            "VALUES (?, ?, ?, 'PENDING', 'PENDING', 'DISABLED')",
                    tenantId,
                    domain,
                    verifyToken);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "domain already bound", ex);
        }
        Long id = platformJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getDomain(id);
    }

    @OperationLog(action = "TENANT_DOMAIN_VERIFY", targetType = "platform_tenant_domain", targetIdArg = 0)
    public TenantDomainVerifyResponse verifyDomain(Long id) {
        TenantDomainResponse domain = getDomain(id);
        boolean cnameMatched = dnsContains(domain.getDomain(), "CNAME", cnameTarget);
        boolean txtMatched = dnsContains(domain.getDomain(), "TXT", domain.getVerifyToken());
        boolean verified = cnameMatched || txtMatched;
        String verifyStatus = verified ? "VERIFIED" : "FAILED";
        String status = verified ? "ACTIVE" : "DISABLED";
        platformJdbcTemplate.update(
                "UPDATE platform_tenant_domain SET verify_status = ?, status = ? WHERE id = ?",
                verifyStatus,
                status,
                id);
        TenantDomainVerifyResponse response = new TenantDomainVerifyResponse();
        response.setId(id);
        response.setDomain(domain.getDomain());
        response.setVerifyStatus(verifyStatus);
        response.setStatus(status);
        response.setCnameMatched(cnameMatched);
        response.setTxtMatched(txtMatched);
        return response;
    }

    @OperationLog(action = "TENANT_DOMAIN_ENABLE", targetType = "platform_tenant_domain", targetIdArg = 0)
    public TenantDomainResponse enableDomain(Long id) {
        TenantDomainResponse domain = getDomain(id);
        if (!"VERIFIED".equals(domain.getVerifyStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "domain is not verified");
        }
        platformJdbcTemplate.update("UPDATE platform_tenant_domain SET status = 'ACTIVE' WHERE id = ?", id);
        return getDomain(id);
    }

    @OperationLog(action = "TENANT_DOMAIN_DISABLE", targetType = "platform_tenant_domain", targetIdArg = 0)
    public TenantDomainResponse disableDomain(Long id) {
        getDomain(id);
        platformJdbcTemplate.update("UPDATE platform_tenant_domain SET status = 'DISABLED' WHERE id = ?", id);
        return getDomain(id);
    }

    private TenantDomainResponse getDomain(Long id) {
        String tenantCode = requireTenantCode();
        List<TenantDomainResponse> domains = platformJdbcTemplate.query(
                "SELECT d.id, t.tenant_code, d.domain, d.verify_token, d.verify_status, d.ssl_status, d.status, d.create_time " +
                        "FROM platform_tenant_domain d JOIN platform_tenant t ON d.tenant_id = t.id " +
                        "WHERE d.id = ? AND t.tenant_code = ?",
                (rs, rowNum) -> {
                    TenantDomainResponse response = new TenantDomainResponse();
                    response.setId(rs.getLong("id"));
                    response.setTenantCode(rs.getString("tenant_code"));
                    response.setDomain(rs.getString("domain"));
                    response.setVerifyToken(rs.getString("verify_token"));
                    response.setCnameTarget(cnameTarget);
                    response.setVerifyStatus(rs.getString("verify_status"));
                    response.setSslStatus(rs.getString("ssl_status"));
                    response.setStatus(rs.getString("status"));
                    response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    return response;
                },
                id,
                tenantCode
            );
        if (domains.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "domain not found");
        }
        return domains.get(0);
    }

    private void assertCustomDomainFeatureEnabled() {
        String tenantCode = requireTenantCode();
        List<String> values = platformJdbcTemplate.query(
                "SELECT f.feature_value FROM platform_tenant t " +
                        "JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "JOIN platform_plan_feature f ON s.plan_id = f.plan_id " +
                        "WHERE t.tenant_code = ? AND f.feature_key = 'custom_domain' " +
                        "ORDER BY s.id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("feature_value"),
                tenantCode
            );
        if (values.isEmpty() || !"true".equalsIgnoreCase(values.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "current plan does not include custom domain");
        }
    }

    private Long tenantId(String tenantCode) {
        List<Long> tenantIds = platformJdbcTemplate.query(
                "SELECT id FROM platform_tenant WHERE tenant_code = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> rs.getLong("id"),
                tenantCode
            );
        if (tenantIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant is not active");
        }
        return tenantIds.get(0);
    }

    private boolean dnsContains(String domain, String recordType, String expectedValue) {
        if (!StringUtils.hasText(expectedValue)) {
            return false;
        }
        try {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            Attributes attributes = new InitialDirContext(env).getAttributes(domain, new String[]{recordType});
            Attribute attribute = attributes.get(recordType);
            if (attribute == null) {
                return false;
            }
            NamingEnumeration<?> values = attribute.getAll();
            while (values.hasMore()) {
                String value = normalizeDnsValue(String.valueOf(values.next()));
                if (value.equals(normalizeDnsValue(expectedValue)) || value.contains(normalizeDnsValue(expectedValue))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private String normalizeAndValidateDomain(String domain) {
        String normalized = normalizeDomain(domain);
        if (!DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid domain");
        }
        return normalized;
    }

    private String normalizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return "";
        }
        String normalized = domain.trim().toLowerCase(Locale.ENGLISH);
        if (normalized.endsWith(".")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeDnsValue(String value) {
        return normalizeDomain(value.replace("\"", ""));
    }

    private String requireTenantCode() {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        return tenantCode;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
