package com.analyzer.api.dto.contract;

import com.analyzer.api.enums.ContractStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractResponse {

    private String id;
    private Long ownerId;
    private String workspaceId;
    private Long templateId;
    private String generationJobId;
    private String sourceDocumentId;
    private String title;
    private String contractType;
    private ContractStatus status;
    private Integer currentVersionNo;
    private String currentContentHash;
    private LocalDateTime lastGeneratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
