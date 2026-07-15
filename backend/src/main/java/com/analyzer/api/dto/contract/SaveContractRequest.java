package com.analyzer.api.dto.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveContractRequest {

    @NotNull
    private String workspaceId;

    private Long templateId;

    private String generationJobId;

    private String sourceDocumentId;

    @NotBlank
    private String title;

    @NotBlank
    private String contractType;

    @NotBlank
    private String content;
}
