package com.orbitcrm.file.controller;

import com.orbitcrm.common.core.api.ApiResult;
import com.orbitcrm.common.security.RequiresPermission;
import com.orbitcrm.file.api.FileDownloadResource;
import com.orbitcrm.file.api.FileResponse;
import com.orbitcrm.file.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    @RequiresPermission("file:manage")
    public ApiResult<List<FileResponse>> listFiles(
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) Long bizId) {
        return ApiResult.ok(fileService.listFiles(bizType, bizId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("file:manage")
    public ApiResult<FileResponse> uploadFile(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bizType", required = false) String bizType,
                                              @RequestParam(value = "bizId", required = false) Long bizId) {
        return ApiResult.ok(fileService.uploadFile(file, bizType, bizId));
    }

    @GetMapping("/{id}/download")
    @RequiresPermission("file:manage")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable("id") Long id) {
        FileDownloadResource download = fileService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.getOriginalName()))
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .contentLength(download.getSizeBytes())
                .body(new InputStreamResource(download.getInputStream()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("file:manage")
    public ApiResult<FileResponse> deleteFile(@PathVariable("id") Long id) {
        return ApiResult.ok(fileService.deleteFile(id));
    }

    private String contentDisposition(String filename) {
        try {
            String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return "attachment; filename*=UTF-8''" + encoded;
        } catch (Exception ex) {
            return "attachment";
        }
    }
}
