package com.analyzer.api.dto.contract;

import com.analyzer.api.enums.ContractTemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractTemplateResponse {

    private Long id;
    private String templateCode;
    private String name;
    private String description;
    private String category;
    private String jurisdiction;
    private String content;
    private ContractTemplateStatus status;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
