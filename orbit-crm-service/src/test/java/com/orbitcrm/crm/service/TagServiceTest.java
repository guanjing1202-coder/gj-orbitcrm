package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.CustomerTagAssignRequest;
import com.orbitcrm.crm.api.TagCreateRequest;
import com.orbitcrm.crm.api.TagResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TagServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createTagUpsertsAndReadsCreatedTagFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        TagService service = new TagService(tenantJdbcTemplateProvider);
        TagCreateRequest request = new TagCreateRequest();
        request.setTagName("High Value");
        request.setColor("#2F80ED");

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(8L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(8L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(8L);
                    when(resultSet.getString("tag_name")).thenReturn("High Value");
                    when(resultSet.getString("color")).thenReturn("#2F80ED");
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 16, 10)));
                    return mapper.mapRow(resultSet, 0);
                });

        TagResponse response = service.createTag(request);

        assertNotNull(response);
        assertEquals(8L, response.getId());
        assertEquals("High Value", response.getTagName());
        assertEquals("#2F80ED", response.getColor());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_tag (tag_name, color) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), color = VALUES(color)"),
                eq("High Value"),
                eq("#2F80ED"));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceCustomerTagsDeduplicatesIdsAndReadsTagsFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        TagService service = new TagService(tenantJdbcTemplateProvider);
        CustomerTagAssignRequest request = new CustomerTagAssignRequest();
        request.setTagIds(Arrays.asList(2L, 5L, 2L, null));

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                66L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_tag WHERE id IN (?, ?)",
                Integer.class,
                2L,
                5L)).thenReturn(2);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(66L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet first = mock(ResultSet.class);
                    when(first.getLong("id")).thenReturn(2L);
                    when(first.getString("tag_name")).thenReturn("Important");
                    when(first.getString("color")).thenReturn("#EB5757");
                    when(first.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 16, 20)));
                    ResultSet second = mock(ResultSet.class);
                    when(second.getLong("id")).thenReturn(5L);
                    when(second.getString("tag_name")).thenReturn("Renewal");
                    when(second.getString("color")).thenReturn("#27AE60");
                    when(second.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 16, 21)));
                    return Arrays.asList(mapper.mapRow(first, 0), mapper.mapRow(second, 1));
                });

        List<TagResponse> responses = service.replaceCustomerTags(66L, request);

        assertEquals(2, responses.size());
        assertEquals("Important", responses.get(0).getTagName());
        assertEquals("Renewal", responses.get(1).getTagName());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update("DELETE FROM crm_customer_tag WHERE customer_id = ?", 66L);
        verify(tenantJdbcTemplate).update(
                "INSERT IGNORE INTO crm_customer_tag (customer_id, tag_id) VALUES (?, ?)",
                66L,
                2L);
        verify(tenantJdbcTemplate).update(
                "INSERT IGNORE INTO crm_customer_tag (customer_id, tag_id) VALUES (?, ?)",
                66L,
                5L);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    void addCustomerTagRejectsMissingTagIdBeforeWritingRelation() {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TagService service = new TagService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                66L)).thenReturn(1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.addCustomerTag(66L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(
                "INSERT IGNORE INTO crm_customer_tag (customer_id, tag_id) VALUES (?, ?)",
                66L,
                null);
    }

    @Test
    void removeCustomerTagRejectsMissingTagIdBeforeDeletingRelation() {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        TagService service = new TagService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                66L)).thenReturn(1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.removeCustomerTag(66L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(
                "DELETE FROM crm_customer_tag WHERE customer_id = ? AND tag_id = ?",
                66L,
                null);
    }
}
