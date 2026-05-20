package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.ContactCreateRequest;
import com.orbitcrm.crm.api.ContactResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContactServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createContactReadsCreatedContactFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        ContactService service = new ContactService(tenantJdbcTemplateProvider);
        ContactCreateRequest request = new ContactCreateRequest();
        request.setCustomerId(66L);
        request.setContactName("Katherine Johnson");
        request.setTitle("Director");
        request.setPhone("18800004444");
        request.setEmail("katherine@example.com");
        request.setPrimary(true);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_customer WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                66L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(77L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(77L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(77L);
                    when(resultSet.getLong("customer_id")).thenReturn(66L);
                    when(resultSet.getString("contact_name")).thenReturn("Katherine Johnson");
                    when(resultSet.getString("title")).thenReturn("Director");
                    when(resultSet.getString("phone")).thenReturn("18800004444");
                    when(resultSet.getString("email")).thenReturn("katherine@example.com");
                    when(resultSet.getInt("is_primary")).thenReturn(1);
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 14, 45)));
                    return mapper.mapRow(resultSet, 0);
                });

        ContactResponse response = service.createContact(request);

        assertNotNull(response);
        assertEquals(77L, response.getId());
        assertEquals(66L, response.getCustomerId());
        assertEquals("Katherine Johnson", response.getContactName());
        assertEquals("Director", response.getTitle());
        assertEquals("18800004444", response.getPhone());
        assertEquals("katherine@example.com", response.getEmail());
        assertEquals(Boolean.TRUE, response.getPrimary());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update("UPDATE crm_contact SET is_primary = 0 WHERE customer_id = ?", 66L);
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
