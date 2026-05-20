package com.orbitcrm.crm.service;

import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.crm.api.FollowRecordCreateRequest;
import com.orbitcrm.crm.api.FollowRecordResponse;
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

class FollowRecordServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createFollowRecordReadsCreatedRecordFromSameTenantJdbcTemplate() throws Exception {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        FollowRecordService service = new FollowRecordService(tenantJdbcTemplateProvider);
        LocalDateTime nextFollowTime = LocalDateTime.of(2026, 5, 21, 10, 30);
        FollowRecordCreateRequest request = new FollowRecordCreateRequest();
        request.setRelatedType("lead");
        request.setRelatedId(88L);
        request.setContent("Call back after product demo");
        request.setNextFollowTime(nextFollowTime);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM crm_lead WHERE id = ? AND status <> 'DELETED'",
                Integer.class,
                88L)).thenReturn(1);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(99L);
        when(tenantJdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenAnswer(invocation -> {
                    RowMapper mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getLong("id")).thenReturn(99L);
                    when(resultSet.getString("related_type")).thenReturn("LEAD");
                    when(resultSet.getLong("related_id")).thenReturn(88L);
                    when(resultSet.getString("content")).thenReturn("Call back after product demo");
                    when(resultSet.getTimestamp("next_follow_time"))
                            .thenReturn(Timestamp.valueOf(nextFollowTime));
                    when(resultSet.getObject("creator_user_id")).thenReturn(null);
                    when(resultSet.getTimestamp("create_time"))
                            .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 20, 15, 20)));
                    return mapper.mapRow(resultSet, 0);
                });

        FollowRecordResponse response = service.createFollowRecord(request);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals("LEAD", response.getRelatedType());
        assertEquals(88L, response.getRelatedId());
        assertEquals("Call back after product demo", response.getContent());
        assertEquals(nextFollowTime, response.getNextFollowTime());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                eq("INSERT INTO crm_follow_record (related_type, related_id, content, next_follow_time, creator_user_id) " +
                        "VALUES (?, ?, ?, ?, ?)"),
                eq("LEAD"),
                eq(88L),
                eq("Call back after product demo"),
                eq(Timestamp.valueOf(nextFollowTime)),
                eq(null));
        verify(tenantJdbcTemplate).queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        verifyNoInteractions(refreshedJdbcTemplate);
    }
}
