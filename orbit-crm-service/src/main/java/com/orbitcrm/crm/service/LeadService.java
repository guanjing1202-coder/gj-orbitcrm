package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.api.LeadCreateRequest;
import com.orbitcrm.crm.api.LeadResponse;
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
import java.util.List;

@Service
public class LeadService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final PlanLimitService planLimitService;

    public LeadService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                       PlanLimitService planLimitService) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.planLimitService = planLimitService;
    }

    public List<LeadResponse> listLeads(String status) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        if (StringUtils.hasText(status)) {
            return jdbcTemplate.query(
                    "SELECT id, lead_name, company_name, phone, email, status, owner_user_id, source, create_time " +
                            "FROM crm_lead WHERE status = ? ORDER BY id DESC LIMIT 100",
                    (rs, rowNum) -> mapLead(rs),
                    status
                );
        }
        return jdbcTemplate.query(
                "SELECT id, lead_name, company_name, phone, email, status, owner_user_id, source, create_time " +
                        "FROM crm_lead WHERE status <> 'DELETED' ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapLead(rs));
    }

    @OperationLog(action = "LEAD_CREATE", targetType = "crm_lead")
    public LeadResponse createLead(LeadCreateRequest request) {
        planLimitService.assertLeadLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO crm_lead (lead_name, company_name, phone, email, status, owner_user_id, source) " +
                        "VALUES (?, ?, ?, ?, 'NEW', ?, ?)",
                request.getLeadName(),
                request.getCompanyName(),
                request.getPhone(),
                request.getEmail(),
                request.getOwnerUserId(),
                request.getSource());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getLead(jdbcTemplate, id);
    }

    @OperationLog(action = "LEAD_STATUS_UPDATE", targetType = "crm_lead", targetIdArg = 0)
    public LeadResponse updateStatus(Long id, String status) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_lead SET status = ? WHERE id = ? AND status <> 'DELETED'",
                status,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "lead not found");
        }
        return getLead(jdbcTemplate, id);
    }

    @OperationLog(action = "LEAD_CONVERT_TO_CUSTOMER", targetType = "crm_lead", targetIdArg = 0)
    public CustomerResponse convertToCustomer(Long id) {
        planLimitService.assertCustomerLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        LeadResponse lead = getLead(jdbcTemplate, id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "lead already converted");
        }
        String customerName = StringUtils.hasText(lead.getCompanyName()) ? lead.getCompanyName() : lead.getLeadName();
        jdbcTemplate.update(
                "INSERT INTO crm_customer (customer_name, phone, email, owner_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                customerName,
                lead.getPhone(),
                lead.getEmail(),
                lead.getOwnerUserId());
        Long customerId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("UPDATE crm_lead SET status = 'CONVERTED' WHERE id = ?", id);
        return jdbcTemplate.queryForObject(
                "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                        "FROM crm_customer WHERE id = ?",
                (rs, rowNum) -> {
                    CustomerResponse response = new CustomerResponse();
                    response.setId(rs.getLong("id"));
                    response.setCustomerName(rs.getString("customer_name"));
                    response.setCustomerType(rs.getString("customer_type"));
                    response.setPhone(rs.getString("phone"));
                    response.setEmail(rs.getString("email"));
                    response.setAddress(rs.getString("address"));
                    response.setOwnerUserId(longOrNull(rs.getObject("owner_user_id")));
                    response.setStatus(rs.getString("status"));
                    response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
                    return response;
                },
                customerId
            );
    }

    private LeadResponse getLead(Long id) {
        return getLead(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    private LeadResponse getLead(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, lead_name, company_name, phone, email, status, owner_user_id, source, create_time " +
                            "FROM crm_lead WHERE id = ? AND status <> 'DELETED'",
                    (rs, rowNum) -> mapLead(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "lead not found", ex);
        }
    }

    private LeadResponse mapLead(ResultSet rs) throws SQLException {
        LeadResponse response = new LeadResponse();
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
