package com.orbitcrm.file.api;

import java.io.InputStream;

public class FileDownloadResource {
    private final InputStream inputStream;
    private final String originalName;
    private final String contentType;
    private final Long sizeBytes;

    public FileDownloadResource(InputStream inputStream, String originalName, String contentType, Long sizeBytes) {
        this.inputStream = inputStream;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }
}
