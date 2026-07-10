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
public class GenerateContractRequest {

    @NotBlank
    private String requestId;

    @NotNull
    private String workspaceId;

    private Long templateId;

    private String sourceDocumentId;

    @NotBlank
    private String inputJson;
}
