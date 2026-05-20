package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.crm.api.CustomerTagAssignRequest;
import com.orbitcrm.crm.api.TagCreateRequest;
import com.orbitcrm.crm.api.TagResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TagService {
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;

    public TagService(TenantJdbcTemplateProvider tenantJdbcTemplateProvider) {
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
    }

    public List<TagResponse> listTags() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, tag_name, color, create_time FROM crm_tag ORDER BY tag_name, id LIMIT 200",
                (rs, rowNum) -> mapTag(rs));
    }

    @OperationLog(action = "TAG_CREATE", targetType = "crm_tag")
    public TagResponse createTag(TagCreateRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        jdbcTemplate.update(
                "INSERT INTO crm_tag (tag_name, color) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), color = VALUES(color)",
                request.getTagName(),
                request.getColor());
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getTag(jdbcTemplate, id);
    }

    public List<TagResponse> listCustomerTags(Long customerId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertCustomerExists(jdbcTemplate, customerId);
        return listCustomerTags(jdbcTemplate, customerId);
    }

    @OperationLog(action = "CUSTOMER_TAG_REPLACE", targetType = "crm_customer", targetIdArg = 0)
    public List<TagResponse> replaceCustomerTags(Long customerId, CustomerTagAssignRequest request) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertCustomerExists(jdbcTemplate, customerId);
        List<Long> tagIds = normalizeTagIds(request == null ? null : request.getTagIds());
        assertTagsExist(jdbcTemplate, tagIds);
        jdbcTemplate.update("DELETE FROM crm_customer_tag WHERE customer_id = ?", customerId);
        for (Long tagId : tagIds) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO crm_customer_tag (customer_id, tag_id) VALUES (?, ?)",
                    customerId,
                    tagId);
        }
        return listCustomerTags(jdbcTemplate, customerId);
    }

    @OperationLog(action = "CUSTOMER_TAG_ADD", targetType = "crm_customer", targetIdArg = 0)
    public List<TagResponse> addCustomerTag(Long customerId, Long tagId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertCustomerExists(jdbcTemplate, customerId);
        assertTagsExist(jdbcTemplate, singleTagId(tagId));
        jdbcTemplate.update(
                "INSERT IGNORE INTO crm_customer_tag (customer_id, tag_id) VALUES (?, ?)",
                customerId,
                tagId);
        return listCustomerTags(jdbcTemplate, customerId);
    }

    @OperationLog(action = "CUSTOMER_TAG_REMOVE", targetType = "crm_customer", targetIdArg = 0)
    public List<TagResponse> removeCustomerTag(Long customerId, Long tagId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertCustomerExists(jdbcTemplate, customerId);
        jdbcTemplate.update(
                "DELETE FROM crm_customer_tag WHERE customer_id = ? AND tag_id = ?",
                customerId,
                tagId);
        return listCustomerTags(jdbcTemplate, customerId);
    }

    private TagResponse getTag(JdbcTemplate jdbcTemplate, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, tag_name, color, create_time FROM crm_tag WHERE id = ?",
                    (rs, rowNum) -> mapTag(rs),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tag not found", ex);
        }
    }

    private List<TagResponse> listCustomerTags(JdbcTemplate jdbcTemplate, Long customerId) {
        return jdbcTemplate.query(
                "SELECT t.id, t.tag_name, t.color, t.create_time " +
                        "FROM crm_tag t JOIN crm_customer_tag ct ON t.id = ct.tag_id " +
                        "WHERE ct.customer_id = ? ORDER BY t.tag_name, t.id",
                (rs, rowNum) -> mapTag(rs),
                customerId);
    }

    private void assertCustomerExists(JdbcTemplate jdbcTemplate, Long customerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                customerId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
    }

    private void assertTagsExist(JdbcTemplate jdbcTemplate, List<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_tag WHERE id IN (" + placeholders(tagIds.size()) + ")",
                Integer.class,
                tagIds.toArray());
        if (count == null || count != tagIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tag does not exist");
        }
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        Set<Long> normalized = new LinkedHashSet<Long>();
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                if (tagId != null) {
                    normalized.add(tagId);
                }
            }
        }
        return new ArrayList<Long>(normalized);
    }

    private List<Long> singleTagId(Long tagId) {
        List<Long> tagIds = new ArrayList<Long>();
        if (tagId != null) {
            tagIds.add(tagId);
        }
        return tagIds;
    }

    private String placeholders(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("?");
        }
        return builder.toString();
    }

    private TagResponse mapTag(ResultSet rs) throws SQLException {
        TagResponse response = new TagResponse();
        response.setId(rs.getLong("id"));
        response.setTagName(rs.getString("tag_name"));
        response.setColor(rs.getString("color"));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
