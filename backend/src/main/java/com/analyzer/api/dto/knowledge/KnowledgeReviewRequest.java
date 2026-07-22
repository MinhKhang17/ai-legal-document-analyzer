package com.analyzer.api.dto.knowledge;

import com.analyzer.api.enums.KnowledgeReviewDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReviewRequest {

    @NotNull
    private KnowledgeReviewDecision decision;

    @Size(max = 2000)
    private String note;
}
