package com.analyzer.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateContractApiRequest {
    private String requestId;
    private String templateContent;
    private String inputJson;
    private String contractType;
}
