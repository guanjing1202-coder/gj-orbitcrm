package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.CustomerCreateRequest;
import com.orbitcrm.crm.api.CustomerResponse;
import com.orbitcrm.crm.api.CustomerUpdateRequest;
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
import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final PlanLimitService planLimitService;

    public CustomerService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                           PlanLimitService planLimitService) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.planLimitService = planLimitService;
    }

    public List<CustomerResponse> listCustomers() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                        "FROM crm_customer WHERE status <> 'DELETED' ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapCustomer(rs));
    }

    public List<CustomerResponse> listDeletedCustomers() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                        "FROM crm_customer WHERE status = 'DELETED' ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapCustomer(rs));
    }

    @OperationLog(action = "CUSTOMER_CREATE", targetType = "crm_customer")
    public CustomerResponse createCustomer(CustomerCreateRequest request) {
        planLimitService.assertCustomerLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO crm_customer (customer_name, customer_type, phone, email, address, owner_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')",
                request.getCustomerName(),
                request.getCustomerType(),
                request.getPhone(),
                request.getEmail(),
                request.getAddress(),
                request.getOwnerUserId());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getCustomerIncludingDeleted(jdbcTemplate, id);
    }

    public CustomerResponse getCustomer(Long id) {
        return getActiveCustomer(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    @OperationLog(action = "CUSTOMER_UPDATE", targetType = "crm_customer", targetIdArg = 0)
    public CustomerResponse updateCustomer(Long id, CustomerUpdateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        CustomerResponse existing = getActiveCustomer(jdbcTemplate, id);
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE crm_customer SET ");
        boolean hasUpdate = false;
        if (StringUtils.hasText(request.getCustomerName())) {
            hasUpdate = appendAssignment(sql, hasUpdate, "customer_name = ?");
            values.add(request.getCustomerName());
        }
        if (request.getCustomerType() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "customer_type = ?");
            values.add(request.getCustomerType());
        }
        if (request.getPhone() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "phone = ?");
            values.add(request.getPhone());
        }
        if (request.getEmail() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "email = ?");
            values.add(request.getEmail());
        }
        if (request.getAddress() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "address = ?");
            values.add(request.getAddress());
        }
        if (request.getOwnerUserId() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "owner_user_id = ?");
            values.add(request.getOwnerUserId());
        }
        if (!hasUpdate) {
            return existing;
        }
        sql.append(" WHERE id = ? AND status <> 'DELETED'");
        values.add(id);
        jdbcTemplate.update(sql.toString(), values.toArray());
        return getActiveCustomer(jdbcTemplate, id);
    }

    @OperationLog(action = "CUSTOMER_TRANSFER", targetType = "crm_customer", targetIdArg = 0)
    public CustomerResponse transferCustomer(Long id, Long ownerUserId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_customer SET owner_user_id = ? WHERE id = ? AND status <> 'DELETED'",
                ownerUserId,
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        return getActiveCustomer(jdbcTemplate, id);
    }

    @OperationLog(action = "CUSTOMER_DELETE", targetType = "crm_customer", targetIdArg = 0)
    public CustomerResponse deleteCustomer(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_customer SET status = 'DELETED' WHERE id = ? AND status <> 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        return getCustomerIncludingDeleted(jdbcTemplate, id);
    }

    @OperationLog(action = "CUSTOMER_RESTORE", targetType = "crm_customer", targetIdArg = 0)
    public CustomerResponse restoreCustomer(Long id) {
        planLimitService.assertCustomerLimitAvailable();
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE crm_customer SET status = 'ACTIVE' WHERE id = ? AND status = 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted customer not found");
        }
        return getActiveCustomer(jdbcTemplate, id);
    }

    private CustomerResponse getActiveCustomer(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                            "FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                    (rs, rowNum) -> mapCustomer(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found", ex);
        }
    }

    private CustomerResponse getCustomerIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                            "FROM crm_customer WHERE id = ?",
                    (rs, rowNum) -> mapCustomer(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found", ex);
        }
    }

    private boolean appendAssignment(StringBuilder sql, boolean hasUpdate, String assignment) {
        if (hasUpdate) {
            sql.append(", ");
        }
        sql.append(assignment);
        return true;
    }

    private CustomerResponse mapCustomer(ResultSet rs) throws SQLException {
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
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
