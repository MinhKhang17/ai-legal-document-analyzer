package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.DocumentPurpose;
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
public class TicketFileResponse {

    private String documentId;
    private String originalFileName;
    private String storedFileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private DocumentPurpose documentPurpose;
    private DocumentVisibilityScope visibilityScope;
    private LocalDateTime uploadedAt;
}
