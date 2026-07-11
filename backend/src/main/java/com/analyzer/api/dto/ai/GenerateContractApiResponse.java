package com.analyzer.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateContractApiResponse {
    private String requestId;
    private String promptSnapshot;
    private String outputDraft;
    private String error;
}
