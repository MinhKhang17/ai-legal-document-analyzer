package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeatureSummaryResponse {

    private String requestId;
    private Double confidenceScore;
    private RiskLevel riskLevel;
    private String summary;
}
