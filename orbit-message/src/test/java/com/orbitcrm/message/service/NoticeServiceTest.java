package com.orbitcrm.message.service;

import com.orbitcrm.common.core.message.NoticeEvent;
import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.message.api.NoticeCreateRequest;
import com.orbitcrm.message.api.NoticeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoticeServiceTest {
    @AfterEach
    void clearUser() {
        CurrentUserContext.clear();
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createNoticeReadsCreatedNoticeFromSameTenantJdbcTemplate() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeCreateRequest request = new NoticeCreateRequest();
        request.setTitle("Renewal reminder");
        request.setContent("Contract renewal is due soon");
        request.setNoticeType("TASK");
        request.setReceiverUserIds(Arrays.asList(18L, 19L));

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(88L);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> Arrays.asList(mapNotice(invocation.getArgument(1), "ACTIVE", null)));

        NoticeResponse response = service.createNotice(request);

        assertNotNull(response);
        assertEquals(88L, response.getId());
        assertEquals("Renewal reminder", response.getTitle());
        assertEquals("TASK", response.getNoticeType());
        assertEquals(12L, response.getSenderUserId());
        assertEquals("ACTIVE", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplate).update(
                "INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "Renewal reminder",
                "Contract renewal is due soon",
                "TASK",
                12L);
        verify(tenantJdbcTemplate).update(
                "INSERT INTO sys_notice_receiver (notice_id, user_id) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)",
                88L,
                18L);
        verify(tenantJdbcTemplate).update(
                "INSERT INTO sys_notice_receiver (notice_id, user_id) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)",
                88L,
                19L);
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createNoticeTrimsTextFieldsBeforeInsertingNotice() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeCreateRequest request = new NoticeCreateRequest();
        request.setTitle(" Renewal reminder ");
        request.setContent(" Contract renewal is due soon ");
        request.setNoticeType(" TASK ");
        request.setReceiverUserIds(Arrays.asList(18L));

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(88L);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> Arrays.asList(mapNotice(invocation.getArgument(1), "ACTIVE", null)));

        service.createNotice(request);

        verify(tenantJdbcTemplate).update(
                "INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "Renewal reminder",
                "Contract renewal is due soon",
                "TASK",
                12L);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void markReadReadsNoticeFromSameTenantJdbcTemplate() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate refreshedJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        LocalDateTime readTime = LocalDateTime.of(2026, 5, 21, 10, 30);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate())
                .thenReturn(tenantJdbcTemplate, refreshedJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_notice_receiver SET read_time = NOW() WHERE notice_id = ? AND user_id = ? AND read_time IS NULL",
                88L,
                18L)).thenReturn(1);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(88L), eq(18L)))
                .thenAnswer(invocation -> Arrays.asList(mapNotice(invocation.getArgument(1), "ACTIVE", readTime)));

        NoticeResponse response = service.markRead(88L);

        assertNotNull(response);
        assertEquals(88L, response.getId());
        assertEquals(readTime, response.getReadTime());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
        verifyNoInteractions(refreshedJdbcTemplate);
    }

    @Test
    void markAllReadReturnsUpdatedCountFromCurrentTenantJdbcTemplate() {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_notice_receiver r JOIN sys_notice n ON r.notice_id = n.id " +
                        "SET r.read_time = NOW() WHERE r.user_id = ? AND r.read_time IS NULL AND n.status = 'ACTIVE'",
                18L)).thenReturn(3);

        Integer response = service.markAllRead();

        assertEquals(3, response);
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listNoticesTrimsStatusAndNoticeTypeBeforeQueryingTenantNotices() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);

        service.listNotices(" ACTIVE ", " TASK ", 20);

        verify(tenantJdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq("ACTIVE"),
                eq("TASK"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deleteNoticeSoftDeletesNoticeAndReturnsDeletedNotice() throws Exception {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.update(
                "UPDATE sys_notice SET status = 'DELETED' WHERE id = ? AND status = 'ACTIVE'",
                88L)).thenReturn(1);
        when(tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(88L)))
                .thenAnswer(invocation -> Arrays.asList(mapNotice(invocation.getArgument(1), "DELETED", null)));

        NoticeResponse response = service.deleteNotice(88L);

        assertEquals("DELETED", response.getStatus());
        verify(tenantJdbcTemplateProvider, times(1)).currentTenantJdbcTemplate();
    }

    @Test
    void getNoticeRejectsMissingIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getNotice(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void listNoticesRejectsMissingTenantBeforeReadingTenant() {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.listNotices(null, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void listMyNoticesRejectsMissingTenantBeforeReadingTenant() {
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.listMyNotices(false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void unreadCountRejectsMissingTenantBeforeReadingTenant() {
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::unreadCount);

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void markAllReadRejectsMissingTenantBeforeReadingTenant() {
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::markAllRead);

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void getNoticeRejectsNonPositiveIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getNotice(0L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void markReadRejectsMissingIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markRead(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void markUnreadRejectsMissingIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.markUnread(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void getMyNoticeRejectsMissingIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(18L, "bob", "demo-company"));
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getMyNotice(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void deleteNoticeRejectsMissingIdBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deleteNotice(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void createNoticeRejectsMissingRequestBeforeReadingTenant() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNotice(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void createNoticeRejectsMissingReceiversBeforeInsertingNotice() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeCreateRequest request = new NoticeCreateRequest();
        request.setTitle("Renewal reminder");
        request.setContent("Contract renewal is due soon");

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        when(tenantJdbcTemplate.query(
                eq("SELECT id FROM sys_user WHERE status = 'ACTIVE'"),
                any(RowMapper.class))).thenReturn(Collections.emptyList());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNotice(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(
                eq("INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void createNoticeRejectsNullReceiverIdBeforeInsertingNotice() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeCreateRequest request = new NoticeCreateRequest();
        request.setTitle("Renewal reminder");
        request.setContent("Contract renewal is due soon");
        request.setReceiverUserIds(Arrays.asList(18L, null));

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNotice(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(
                eq("INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void createNoticeRejectsNonPositiveReceiverIdBeforeInsertingNotice() {
        TenantContext.setTenantCode("demo-company");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeCreateRequest request = new NoticeCreateRequest();
        request.setTitle("Renewal reminder");
        request.setContent("Contract renewal is due soon");
        request.setReceiverUserIds(Arrays.asList(18L, 0L));

        when(tenantJdbcTemplateProvider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNotice(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(tenantJdbcTemplate, never()).update(
                eq("INSERT INTO sys_notice (title, content, notice_type, sender_user_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')"),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void createNoticeFromEventRejectsMissingEventWithoutChangingTenantContext() {
        TenantContext.setTenantCode("existing-tenant");
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNoticeFromEvent(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("existing-tenant", TenantContext.getTenantCode());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    @Test
    void createNoticeFromEventClearsTenantContextAfterValidationFailure() {
        TenantJdbcTemplateProvider tenantJdbcTemplateProvider = mock(TenantJdbcTemplateProvider.class);
        NoticeService service = new NoticeService(tenantJdbcTemplateProvider);
        NoticeEvent event = new NoticeEvent();
        event.setTenantCode("demo-company");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createNoticeFromEvent(event));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(null, TenantContext.getTenantCode());
        verifyNoInteractions(tenantJdbcTemplateProvider);
    }

    private NoticeResponse mapNotice(RowMapper<NoticeResponse> mapper,
                                     String status,
                                     LocalDateTime readTime) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(88L);
        when(resultSet.getString("title")).thenReturn("Renewal reminder");
        when(resultSet.getString("content")).thenReturn("Contract renewal is due soon");
        when(resultSet.getString("notice_type")).thenReturn("TASK");
        when(resultSet.getObject("sender_user_id")).thenReturn(12L);
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("read_time"))
                .thenReturn(readTime == null ? null : Timestamp.valueOf(readTime));
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }
}
