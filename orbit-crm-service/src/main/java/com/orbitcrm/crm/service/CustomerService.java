package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.CustomerCreateRequest;
import com.orbitcrm.crm.api.CustomerResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
                });
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
        return getCustomer(id);
    }

    private CustomerResponse getCustomer(Long id) {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT id, customer_name, customer_type, phone, email, address, owner_user_id, status, create_time " +
                        "FROM crm_customer WHERE id = ?",
                new Object[]{id},
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
                });
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
