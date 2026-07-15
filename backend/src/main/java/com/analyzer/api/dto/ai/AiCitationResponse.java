package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.CitationSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCitationResponse {

    private String id;
    private CitationSourceType sourceType;
    private String sourceReferenceId;
    private String label;
    private String excerpt;
    private Integer pageNumber;
    private Integer chunkIndex;
    private Double score;
    private String uri;
}
