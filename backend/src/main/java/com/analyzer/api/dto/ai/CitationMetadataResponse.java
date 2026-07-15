package com.analyzer.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationMetadataResponse {

    private String sourceId;
    private List<AiCitationResponse> citations;
}
