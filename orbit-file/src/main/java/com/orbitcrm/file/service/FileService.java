package com.orbitcrm.file.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.file.api.FileDownloadResource;
import com.orbitcrm.file.api.FileResponse;
import com.orbitcrm.file.api.FileUsageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileService {
    private final JdbcTemplate platformJdbcTemplate;
    private final TenantJdbcTemplateProvider tenantJdbcTemplateProvider;
    private final MinioStorageService minioStorageService;
    private final String bucketPrefix;
    private final long maxUploadBytes;

    public FileService(JdbcTemplate platformJdbcTemplate,
                       TenantJdbcTemplateProvider tenantJdbcTemplateProvider,
                       MinioStorageService minioStorageService,
                       @Value("${orbit.file.bucket-prefix}") String bucketPrefix,
                       @Value("${orbit.file.max-upload-bytes}") long maxUploadBytes) {
        this.platformJdbcTemplate = platformJdbcTemplate;
        this.tenantJdbcTemplateProvider = tenantJdbcTemplateProvider;
        this.minioStorageService = minioStorageService;
        this.bucketPrefix = bucketPrefix;
        this.maxUploadBytes = maxUploadBytes;
    }

    public List<FileResponse> listFiles(String bizType, Long bizId) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        if (StringUtils.hasText(bizType) && bizId != null) {
            return jdbcTemplate.query(
                    "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                            "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                            "WHERE status = 'ACTIVE' AND biz_type = ? AND biz_id = ? ORDER BY id DESC",
                    (rs, rowNum) -> mapFile(rs),
                    bizType,
                    bizId
                );
        }
        return jdbcTemplate.query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 200",
                (rs, rowNum) -> mapFile(rs));
    }

    public List<FileResponse> listDeletedFiles() {
        return tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE status = 'DELETED' ORDER BY id DESC LIMIT 200",
                (rs, rowNum) -> mapFile(rs));
    }

    public FileUsageResponse usage() {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        Long usedBytes = activeUsedBytes(jdbcTemplate);
        Integer activeFileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_file WHERE status = 'ACTIVE'",
                Integer.class);
        long quotaBytes = storageQuotaBytes();
        FileUsageResponse response = new FileUsageResponse();
        response.setUsedBytes(usedBytes);
        response.setQuotaBytes(quotaBytes);
        response.setUnlimited(quotaBytes < 0);
        response.setRemainingBytes(quotaBytes < 0 ? -1L : Math.max(0L, quotaBytes - usedBytes));
        response.setActiveFileCount(activeFileCount == null ? 0 : activeFileCount);
        return response;
    }

    @OperationLog(action = "FILE_UPLOAD", targetType = "sys_file")
    public FileResponse uploadFile(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is too large");
        }
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        assertStorageQuotaAvailable(jdbcTemplate, file.getSize());

        String tenantCode = requireTenantCode();
        String bucketName = bucketName(tenantCode);
        String objectKey = objectKey(tenantCode, file.getOriginalFilename());
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";

        try {
            minioStorageService.putObject(bucketName, objectKey, file.getInputStream(), file.getSize(), contentType);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read upload file", ex);
        }

        Long uploaderUserId = currentUserId();
        jdbcTemplate.update(
                "INSERT INTO sys_file (biz_type, biz_id, bucket_name, object_key, original_name, content_type, size_bytes, uploader_user_id, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                bizType,
                bizId,
                bucketName,
                objectKey,
                safeOriginalName(file.getOriginalFilename()),
                contentType,
                file.getSize(),
                uploaderUserId);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getFile(jdbcTemplate, id);
    }

    public FileResponse getFile(Long id) {
        return getFile(tenantJdbcTemplateProvider.currentTenantJdbcTemplate(), id);
    }

    public FileDownloadResource downloadFile(Long id) {
        FileResponse file = getFile(id);
        return new FileDownloadResource(
                minioStorageService.getObject(file.getBucketName(), file.getObjectKey()),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes());
    }

    @OperationLog(action = "FILE_DELETE", targetType = "sys_file", targetIdArg = 0)
    public FileResponse deleteFile(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        int updated = jdbcTemplate.update(
                "UPDATE sys_file SET status = 'DELETED' WHERE id = ? AND status = 'ACTIVE'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        return getFileIncludingDeleted(jdbcTemplate, id);
    }

    @OperationLog(action = "FILE_RESTORE", targetType = "sys_file", targetIdArg = 0)
    public FileResponse restoreFile(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        FileResponse file = getFileIncludingDeleted(jdbcTemplate, id);
        if (!"DELETED".equals(file.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted file not found");
        }
        assertStorageQuotaAvailable(jdbcTemplate, file.getSizeBytes());
        jdbcTemplate.update(
                "UPDATE sys_file SET status = 'ACTIVE' WHERE id = ? AND status = 'DELETED'",
                id);
        return getFile(jdbcTemplate, id);
    }

    @OperationLog(action = "FILE_PURGE", targetType = "sys_file", targetIdArg = 0)
    public FileResponse purgeFile(Long id) {
        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
        FileResponse file = getFileIncludingDeleted(jdbcTemplate, id);
        if (!"DELETED".equals(file.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted file not found");
        }
        int updated = jdbcTemplate.update(
                "UPDATE sys_file SET status = 'PURGED' WHERE id = ? AND status = 'DELETED'",
                id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "deleted file not found");
        }
        minioStorageService.removeObject(file.getBucketName(), file.getObjectKey());
        file.setStatus("PURGED");
        return file;
    }

    private FileResponse getFile(JdbcTemplate jdbcTemplate, Long id) {
        List<FileResponse> files = jdbcTemplate.query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> mapFile(rs),
                id
            );
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        return files.get(0);
    }

    private FileResponse getFileIncludingDeleted(JdbcTemplate jdbcTemplate, Long id) {
        List<FileResponse> files = jdbcTemplate.query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE id = ? AND status <> 'PURGED'",
                (rs, rowNum) -> mapFile(rs),
                id
            );
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        return files.get(0);
    }

    private void assertStorageQuotaAvailable(JdbcTemplate jdbcTemplate, long incomingBytes) {
        long quotaBytes = storageQuotaBytes();
        if (quotaBytes < 0) {
            return;
        }
        long used = activeUsedBytes(jdbcTemplate);
        if (used + incomingBytes > quotaBytes) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "file storage quota exceeded");
        }
    }

    private long activeUsedBytes(JdbcTemplate jdbcTemplate) {
        Long usedBytes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class);
        return usedBytes == null ? 0L : usedBytes;
    }

    private long storageQuotaBytes() {
        String tenantCode = requireTenantCode();
        List<String> values = platformJdbcTemplate.query(
                "SELECT f.feature_value FROM platform_tenant t " +
                        "JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "JOIN platform_plan_feature f ON s.plan_id = f.plan_id " +
                        "WHERE t.tenant_code = ? AND f.feature_key = 'file_storage_gb' " +
                        "ORDER BY s.id DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("feature_value"),
                tenantCode
            );
        if (values.isEmpty()) {
            return -1L;
        }
        long gb = Long.parseLong(values.get(0));
        return gb < 0 ? -1L : gb * 1024L * 1024L * 1024L;
    }

    private FileResponse mapFile(ResultSet rs) throws SQLException {
        FileResponse response = new FileResponse();
        response.setId(rs.getLong("id"));
        response.setBizType(rs.getString("biz_type"));
        response.setBizId(longOrNull(rs.getObject("biz_id")));
        response.setBucketName(rs.getString("bucket_name"));
        response.setObjectKey(rs.getString("object_key"));
        response.setOriginalName(rs.getString("original_name"));
        response.setContentType(rs.getString("content_type"));
        response.setSizeBytes(rs.getLong("size_bytes"));
        response.setUploaderUserId(longOrNull(rs.getObject("uploader_user_id")));
        response.setStatus(rs.getString("status"));
        response.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
        return response;
    }

    private String bucketName(String tenantCode) {
        return sanitizeBucketPart(bucketPrefix) + "-" + sanitizeBucketPart(tenantCode);
    }

    private String objectKey(String tenantCode, String originalFilename) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return tenantCode + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + extension(originalFilename);
    }

    private String extension(String originalFilename) {
        String safeName = safeOriginalName(originalFilename);
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dot).toLowerCase(Locale.ENGLISH);
    }

    private String safeOriginalName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "unnamed";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private String sanitizeBucketPart(String value) {
        return value.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9-]", "-");
    }

    private String requireTenantCode() {
        String tenantCode = TenantContext.getTenantCode();
        if (!StringUtils.hasText(tenantCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenant is required");
        }
        return tenantCode;
    }

    private Long currentUserId() {
        CurrentUser currentUser = CurrentUserContext.get();
        return currentUser == null ? null : currentUser.getUserId();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long longOrNull(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
