package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.ContactCreateRequest;
import com.orbitcrm.crm.api.ContactResponse;
import com.orbitcrm.crm.api.ContactUpdateRequest;
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
public class ContactService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public ContactService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<ContactResponse> listContacts(Long customerId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        if (customerId != null) {
            return jdbcTemplate.query(
                    "SELECT id, customer_id, contact_name, title, phone, email, is_primary, create_time " +
                            "FROM crm_contact WHERE customer_id = ? ORDER BY is_primary DESC, id DESC LIMIT 100",
                    (rs, rowNum) -> mapContact(rs),
                    customerId
                );
        }
        return jdbcTemplate.query(
                "SELECT id, customer_id, contact_name, title, phone, email, is_primary, create_time " +
                        "FROM crm_contact ORDER BY id DESC LIMIT 100",
                (rs, rowNum) -> mapContact(rs));
    }

    @OperationLog(action = "CONTACT_CREATE", targetType = "crm_contact")
    public ContactResponse createContact(ContactCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertCustomerExists(jdbcTemplate, request.getCustomerId());
        boolean primary = Boolean.TRUE.equals(request.getPrimary());
        if (primary) {
            clearPrimary(jdbcTemplate, request.getCustomerId());
        }
        jdbcTemplate.update(
                "INSERT INTO crm_contact (customer_id, contact_name, title, phone, email, is_primary) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                request.getCustomerId(),
                request.getContactName(),
                request.getTitle(),
                request.getPhone(),
                request.getEmail(),
                primary ? 1 : 0);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getContact(jdbcTemplate, id);
    }

    @OperationLog(action = "CONTACT_UPDATE", targetType = "crm_contact", targetIdArg = 0)
    public ContactResponse updateContact(Long id, ContactUpdateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        ContactResponse contact = getContact(jdbcTemplate, id);
        List<Object> values = new ArrayList<Object>();
        StringBuilder sql = new StringBuilder("UPDATE crm_contact SET ");
        boolean hasUpdate = false;
        if (StringUtils.hasText(request.getContactName())) {
            hasUpdate = appendAssignment(sql, hasUpdate, "contact_name = ?");
            values.add(request.getContactName());
        }
        if (request.getTitle() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "title = ?");
            values.add(request.getTitle());
        }
        if (request.getPhone() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "phone = ?");
            values.add(request.getPhone());
        }
        if (request.getEmail() != null) {
            hasUpdate = appendAssignment(sql, hasUpdate, "email = ?");
            values.add(request.getEmail());
        }
        if (request.getPrimary() != null) {
            if (Boolean.TRUE.equals(request.getPrimary())) {
                clearPrimary(jdbcTemplate, contact.getCustomerId());
            }
            hasUpdate = appendAssignment(sql, hasUpdate, "is_primary = ?");
            values.add(Boolean.TRUE.equals(request.getPrimary()) ? 1 : 0);
        }
        if (!hasUpdate) {
            return contact;
        }
        sql.append(" WHERE id = ?");
        values.add(id);
        jdbcTemplate.update(sql.toString(), values.toArray());
        return getContact(jdbcTemplate, id);
    }

    @OperationLog(action = "CONTACT_SET_PRIMARY", targetType = "crm_contact", targetIdArg = 0)
    public ContactResponse setPrimary(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        ContactResponse contact = getContact(jdbcTemplate, id);
        clearPrimary(jdbcTemplate, contact.getCustomerId());
        jdbcTemplate.update("UPDATE crm_contact SET is_primary = 1 WHERE id = ?", id);
        return getContact(jdbcTemplate, id);
    }

    private ContactResponse getContact(Long id) {
        return getContact(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    private ContactResponse getContact(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, customer_id, contact_name, title, phone, email, is_primary, create_time " +
                            "FROM crm_contact WHERE id = ?",
                    (rs, rowNum) -> mapContact(rs),
                    id
                );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "contact not found", ex);
        }
    }

    private void assertCustomerExists(JdbcTemplate jdbcTemplate, Long customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                customerId
            );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
    }

    private void clearPrimary(JdbcTemplate jdbcTemplate, Long customerId) {
        jdbcTemplate.update("UPDATE crm_contact SET is_primary = 0 WHERE customer_id = ?", customerId);
    }

    private boolean appendAssignment(StringBuilder sql, boolean hasUpdate, String assignment) {
        if (hasUpdate) {
            sql.append(", ");
        }
        sql.append(assignment);
        return true;
    }

    private ContactResponse mapContact(ResultSet rs) throws SQLException {
        ContactResponse response = new ContactResponse();
        response.setId(rs.getLong("id"));
        response.setCustomerId(rs.getLong("customer_id"));
        response.setContactName(rs.getString("contact_name"));
        response.setTitle(rs.getString("title"));
        response.setPhone(rs.getString("phone"));
        response.setEmail(rs.getString("email"));
        response.setPrimary(rs.getInt("is_primary") == 1);
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
