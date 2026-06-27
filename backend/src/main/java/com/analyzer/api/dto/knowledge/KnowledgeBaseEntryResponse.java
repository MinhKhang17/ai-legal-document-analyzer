package com.analyzer.api.dto.knowledge;

import com.analyzer.api.enums.KnowledgeScope;
import com.analyzer.api.enums.KnowledgeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseEntryResponse {

    private String id;
    private String code;
    private String title;
    private String category;
    private KnowledgeScope scope;
    private Integer currentVersionNo;
    private KnowledgeStatus currentStatus;
    private Boolean active;
    private Long createdById;
    private Long workspaceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
