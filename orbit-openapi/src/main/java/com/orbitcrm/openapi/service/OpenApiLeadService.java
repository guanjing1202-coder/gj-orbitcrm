package com.orbitcrm.openapi.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.openapi.api.OpenApiLeadCreateRequest;
import com.orbitcrm.openapi.api.OpenApiLeadResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class OpenApiLeadService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final OpenApiQuotaService openApiQuotaService;

    public OpenApiLeadService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                              OpenApiQuotaService openApiQuotaService) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.openApiQuotaService = openApiQuotaService;
    }

    @OperationLog(action = "OPENAPI_LEAD_CREATE", targetType = "crm_lead")
    public OpenApiLeadResponse createLead(OpenApiLeadCreateRequest request) {
        openApiQuotaService.assertLeadLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO crm_lead (lead_name, company_name, phone, email, status, owner_user_id, source) " +
                        "VALUES (?, ?, ?, ?, 'NEW', ?, ?)",
                request.getLeadName(),
                request.getCompanyName(),
                request.getPhone(),
                request.getEmail(),
                request.getOwnerUserId(),
                sourceOrDefault(request.getSource()));
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getLead(jdbcTemplate, id);
    }

    private OpenApiLeadResponse getLead(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, lead_name, company_name, phone, email, status, owner_user_id, source, create_time " +
                            "FROM crm_lead WHERE id = ?",
                    (rs, rowNum) -> mapLead(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "lead not found", ex);
        }
    }

    private String sourceOrDefault(String source) {
        return StringUtils.hasText(source) ? source : "OPENAPI";
    }

    private OpenApiLeadResponse mapLead(ResultSet rs) throws SQLException {
        OpenApiLeadResponse response = new OpenApiLeadResponse();
        response.setId(rs.getLong("id"));
        response.setLeadName(rs.getString("lead_name"));
        response.setCompanyName(rs.getString("company_name"));
        response.setPhone(rs.getString("phone"));
        response.setEmail(rs.getString("email"));
        response.setStatus(rs.getString("status"));
        response.setOwnerUserId(longOrNull(rs.getObject("owner_user_id")));
        response.setSource(rs.getString("source"));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
