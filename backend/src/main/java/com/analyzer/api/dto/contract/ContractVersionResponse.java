package com.analyzer.api.dto.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractVersionResponse {

    private String id;
    private String contractId;
    private Integer versionNo;
    private String content;
    private String changeSummary;
    private Long generatedById;
    private Boolean generatedByAi;
    private String generationJobId;
    private LocalDateTime createdAt;
}
