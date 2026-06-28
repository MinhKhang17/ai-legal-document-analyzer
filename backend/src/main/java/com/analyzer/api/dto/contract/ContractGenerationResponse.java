package com.analyzer.api.dto.contract;

import com.analyzer.api.enums.ContractGenerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractGenerationResponse {

    private String id;
    private String requestId;
    private Long requesterId;
    private String workspaceId;
    private Long templateId;
    private String sourceDocumentId;
    private String inputJson;
    private String promptSnapshot;
    private String outputDraft;
    private ContractGenerationStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
