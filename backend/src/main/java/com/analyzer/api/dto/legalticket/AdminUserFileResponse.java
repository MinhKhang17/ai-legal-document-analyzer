package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.DocumentVisibilityScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserFileResponse {

    private String documentId;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private DocumentVisibilityScope visibilityScope;
    private LocalDateTime uploadedAt;
}
