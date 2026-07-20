package com.analyzer.api.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ServerBulkIngestFileRequest {
    @NotBlank
    private String relativePath;
    @Size(max = 64)
    private String fileHash;
    @Size(max = 255)
    private String title;
    @Size(max = 100)
    private String code;
    @Size(max = 255)
    private String category;
    @Size(max = 100)
    private String source;
    @Size(max = 255)
    private String version;
    @Size(max = 100)
    private String effectiveDate;
    private boolean dryRun;
    private boolean force;
}
