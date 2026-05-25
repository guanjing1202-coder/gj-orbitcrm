package com.orbitcrm.file.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.file.api.FileResponse;
import com.orbitcrm.file.api.FileUsageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class FileServiceTest {
    @AfterEach
    void clearContext() {
        TenantContext.clear();
        CurrentUserContext.clear();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadFileChecksQuotaAndReadsCreatedFileFromSameTenantJdbcTemplate() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.pdf",
                "application/pdf",
                "demo".getBytes("UTF-8"));

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        FileResponse response = service.uploadFile(file, "CUSTOMER", 66L);

        assertNotNull(response);
        assertEquals(42L, response.getId());
        assertEquals("CUSTOMER", response.getBizType());
        assertEquals(66L, response.getBizId());
        assertEquals("proposal.pdf", response.getOriginalName());
        assertEquals(12L, response.getUploaderUserId());
        assertEquals("ACTIVE", response.getStatus());
        verify(tenantJdbcTemplateProvider.provider, times(1)).currentTenantJdbcTemplate();
        verify(minioStorageService).putObject(
                eq("gj-orbit-demo-company"),
                anyString(),
                any(),
                eq(4L),
                eq("application/pdf"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadFileTrimsBizTypeAndContentTypeBeforePersisting() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.pdf",
                " application/pdf ",
                "demo".getBytes("UTF-8"));

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        service.uploadFile(file, " CUSTOMER ", 66L);

        verify(minioStorageService).putObject(
                eq("gj-orbit-demo-company"),
                startsWith("demo-company/"),
                any(),
                eq(4L),
                eq("application/pdf"));
        verify(tenantJdbcTemplateProvider.tenantJdbcTemplate).update(
                eq("INSERT INTO sys_file (biz_type, biz_id, bucket_name, object_key, original_name, content_type, size_bytes, uploader_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')"),
                eq("CUSTOMER"),
                eq(66L),
                eq("gj-orbit-demo-company"),
                startsWith("demo-company/"),
                eq("proposal.pdf"),
                eq("application/pdf"),
                eq(4L),
                eq(12L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadFileTrimsTenantCodeBeforeQuotaAndStorageOperations() throws Exception {
        TenantContext.setTenantCode(" demo-company ");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.pdf",
                "application/pdf",
                "demo".getBytes("UTF-8"));

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        service.uploadFile(file, "CUSTOMER", 66L);

        verify(platformJdbcTemplate).query(anyString(), any(RowMapper.class), eq("demo-company"));
        verify(minioStorageService).putObject(
                eq("gj-orbit-demo-company"),
                startsWith("demo-company/"),
                any(),
                eq(4L),
                eq("application/pdf"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadFileTrimsOriginalNameBeforePersistingAndBuildingObjectKey() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                " C:\\tmp\\proposal.PDF ",
                "application/pdf",
                "demo".getBytes("UTF-8"));

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        service.uploadFile(file, "CUSTOMER", 66L);

        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).putObject(
                eq("gj-orbit-demo-company"),
                objectKeyCaptor.capture(),
                any(),
                eq(4L),
                eq("application/pdf"));
        assertTrue(objectKeyCaptor.getValue().startsWith("demo-company/"));
        assertTrue(objectKeyCaptor.getValue().endsWith(".pdf"));
        verify(tenantJdbcTemplateProvider.tenantJdbcTemplate).update(
                eq("INSERT INTO sys_file (biz_type, biz_id, bucket_name, object_key, original_name, content_type, size_bytes, uploader_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')"),
                eq("CUSTOMER"),
                eq(66L),
                eq("gj-orbit-demo-company"),
                anyString(),
                eq("proposal.PDF"),
                eq("application/pdf"),
                eq(4L),
                eq(12L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void uploadFileStoresNullBizTypeWhenBizTypeIsBlank() throws Exception {
        TenantContext.setTenantCode("demo-company");
        CurrentUserContext.set(new CurrentUser(12L, "alice", "demo-company"));
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.pdf",
                "application/pdf",
                "demo".getBytes("UTF-8"));

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(0L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        service.uploadFile(file, "   ", 66L);

        verify(tenantJdbcTemplateProvider.tenantJdbcTemplate).update(
                eq("INSERT INTO sys_file (biz_type, biz_id, bucket_name, object_key, original_name, content_type, size_bytes, uploader_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')"),
                eq((String) null),
                eq(66L),
                eq("gj-orbit-demo-company"),
                anyString(),
                eq("proposal.pdf"),
                eq("application/pdf"),
                eq(4L),
                eq(12L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listFilesTrimsBizTypeBeforeQueryingTenantFiles() {
        TenantContext.setTenantCode("demo-company");
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        service.listFiles(" CUSTOMER ", 66L);

        verify(tenantJdbcTemplateProvider.tenantJdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq("CUSTOMER"),
                eq(66L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void deleteFileSoftDeletesMetadataWithoutRemovingObject() throws Exception {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.update(
                "UPDATE sys_file SET status = 'DELETED' WHERE id = ? AND status = 'ACTIVE'",
                42L)).thenReturn(1);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "DELETED")));

        FileResponse response = service.deleteFile(42L);

        assertEquals("DELETED", response.getStatus());
        verify(tenantJdbcTemplateProvider.provider, times(1)).currentTenantJdbcTemplate();
        verifyNoInteractions(minioStorageService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void restoreFileChecksQuotaAndReadsRestoredFileFromSameTenantJdbcTemplate() throws Exception {
        TenantContext.setTenantCode("demo-company");
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(100L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "DELETED")))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "ACTIVE")));

        FileResponse response = service.restoreFile(42L);

        assertEquals("ACTIVE", response.getStatus());
        verify(tenantJdbcTemplateProvider.provider, times(1)).currentTenantJdbcTemplate();
        verify(tenantJdbcTemplateProvider.tenantJdbcTemplate).update(
                "UPDATE sys_file SET status = 'ACTIVE' WHERE id = ? AND status = 'DELETED'",
                42L);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void purgeFileMarksMetadataAndRemovesObject() throws Exception {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenAnswer(invocation -> Arrays.asList(mapFile(invocation.getArgument(1), "DELETED")));
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.update(
                "UPDATE sys_file SET status = 'PURGED' WHERE id = ? AND status = 'DELETED'",
                42L)).thenReturn(1);

        FileResponse response = service.purgeFile(42L);

        assertEquals("PURGED", response.getStatus());
        verify(minioStorageService).removeObject("gj-orbit-demo-company", "demo-company/2026/05/21/file.pdf");
    }

    @Test
    void usageReturnsStorageQuotaAndRemainingBytes() {
        TenantContext.setTenantCode("demo-company");
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class)).thenReturn(1024L);
        when(tenantJdbcTemplateProvider.tenantJdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_file WHERE status = 'ACTIVE'",
                Integer.class)).thenReturn(2);
        when(platformJdbcTemplate.query(anyString(), any(RowMapper.class), eq("demo-company")))
                .thenReturn(Arrays.asList("1"));

        FileUsageResponse response = service.usage();

        assertEquals(1024L, response.getUsedBytes());
        assertEquals(1073741824L, response.getQuotaBytes());
        assertEquals(1073740800L, response.getRemainingBytes());
        assertEquals(Boolean.FALSE, response.getUnlimited());
        assertEquals(2, response.getActiveFileCount());
    }

    @Test
    void uploadFileRejectsMissingTenantBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "proposal.pdf",
                "application/pdf",
                "demo".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.uploadFile(file, "CUSTOMER", 66L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void usageRejectsMissingTenantBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::usage);

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(platformJdbcTemplate);
    }

    @Test
    void listFilesRejectsMissingTenantBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.listFiles("CUSTOMER", 66L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(platformJdbcTemplate);
    }

    @Test
    void listDeletedFilesRejectsMissingTenantBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                service::listDeletedFiles);

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(platformJdbcTemplate);
    }

    @Test
    void getFileRejectsMissingIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getFile(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void getFileRejectsNonPositiveIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getFile(0L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void deleteFileRejectsMissingIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deleteFile(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void downloadFileRejectsMissingIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.downloadFile(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void restoreFileRejectsMissingIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.restoreFile(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void purgeFileRejectsMissingIdBeforeReadingTenant() {
        JdbcTemplate platformJdbcTemplate = mock(JdbcTemplate.class);
        TenantJdbcTemplateProviderMock tenantJdbcTemplateProvider = new TenantJdbcTemplateProviderMock();
        MinioStorageService minioStorageService = mock(MinioStorageService.class);
        FileService service = new FileService(platformJdbcTemplate, tenantJdbcTemplateProvider.provider,
                minioStorageService, "gj-orbit", 1024L * 1024L);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.purgeFile(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(tenantJdbcTemplateProvider.provider);
        verifyNoInteractions(tenantJdbcTemplateProvider.tenantJdbcTemplate);
        verifyNoInteractions(minioStorageService);
    }

    private FileResponse mapFile(RowMapper<FileResponse> mapper, String status) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getString("biz_type")).thenReturn("CUSTOMER");
        when(resultSet.getObject("biz_id")).thenReturn(66L);
        when(resultSet.getString("bucket_name")).thenReturn("gj-orbit-demo-company");
        when(resultSet.getString("object_key")).thenReturn("demo-company/2026/05/21/file.pdf");
        when(resultSet.getString("original_name")).thenReturn("proposal.pdf");
        when(resultSet.getString("content_type")).thenReturn("application/pdf");
        when(resultSet.getLong("size_bytes")).thenReturn(4L);
        when(resultSet.getObject("uploader_user_id")).thenReturn(12L);
        when(resultSet.getString("status")).thenReturn(status);
        when(resultSet.getTimestamp("create_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 5, 21, 9, 30)));
        return mapper.mapRow(resultSet, 0);
    }

    private static class TenantJdbcTemplateProviderMock {
        private final JdbcTemplate tenantJdbcTemplate = mock(JdbcTemplate.class);
        private final com.orbitcrm.common.datasource.TenantJdbcTemplateProvider provider =
                mock(com.orbitcrm.common.datasource.TenantJdbcTemplateProvider.class);

        TenantJdbcTemplateProviderMock() {
            when(provider.currentTenantJdbcTemplate()).thenReturn(tenantJdbcTemplate);
        }
    }
}
