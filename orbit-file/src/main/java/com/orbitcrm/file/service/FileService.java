package com.orbitcrm.file.service;

import com.orbitcrm.common.core.tenant.TenantContext;
import com.orbitcrm.common.datasource.TenantJdbcTemplateProvider;
import com.orbitcrm.common.log.OperationLog;
import com.orbitcrm.common.security.CurrentUser;
import com.orbitcrm.common.security.CurrentUserContext;
import com.orbitcrm.file.api.FileDownloadResource;
import com.orbitcrm.file.api.FileResponse;
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
                    new Object[]{bizType, bizId},
                    (rs, rowNum) -> mapFile(rs));
        }
        return jdbcTemplate.query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 200",
                (rs, rowNum) -> mapFile(rs));
    }

    @OperationLog(action = "FILE_UPLOAD", targetType = "sys_file")
    public FileResponse uploadFile(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is too large");
        }
        assertStorageQuotaAvailable(file.getSize());

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

        JdbcTemplate jdbcTemplate = tenantJdbcTemplateProvider.currentTenantJdbcTemplate();
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
        return getFile(id);
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
        FileResponse file = getFile(id);
        tenantJdbcTemplateProvider.currentTenantJdbcTemplate().update(
                "UPDATE sys_file SET status = 'DELETED' WHERE id = ?",
                id);
        minioStorageService.removeObject(file.getBucketName(), file.getObjectKey());
        file.setStatus("DELETED");
        return file;
    }

    private FileResponse getFile(Long id) {
        List<FileResponse> files = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().query(
                "SELECT id, biz_type, biz_id, bucket_name, object_key, original_name, content_type, " +
                        "size_bytes, uploader_user_id, status, create_time FROM sys_file " +
                        "WHERE id = ? AND status = 'ACTIVE'",
                new Object[]{id},
                (rs, rowNum) -> mapFile(rs));
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        return files.get(0);
    }

    private void assertStorageQuotaAvailable(long incomingBytes) {
        long quotaBytes = storageQuotaBytes();
        if (quotaBytes < 0) {
            return;
        }
        Long usedBytes = tenantJdbcTemplateProvider.currentTenantJdbcTemplate().queryForObject(
                "SELECT COALESCE(SUM(size_bytes), 0) FROM sys_file WHERE status = 'ACTIVE'",
                Long.class);
        long used = usedBytes == null ? 0L : usedBytes;
        if (used + incomingBytes > quotaBytes) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "file storage quota exceeded");
        }
    }

    private long storageQuotaBytes() {
        String tenantCode = requireTenantCode();
        List<String> values = platformJdbcTemplate.query(
                "SELECT f.feature_value FROM platform_tenant t " +
                        "JOIN platform_subscription s ON t.id = s.tenant_id " +
                        "JOIN platform_plan_feature f ON s.plan_id = f.plan_id " +
                        "WHERE t.tenant_code = ? AND f.feature_key = 'file_storage_gb' " +
                        "ORDER BY s.id DESC LIMIT 1",
                new Object[]{tenantCode},
                (rs, rowNum) -> rs.getString("feature_value"));
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
