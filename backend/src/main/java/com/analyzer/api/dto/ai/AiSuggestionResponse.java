package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.SuggestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestionResponse {

    private String requestId;
    private SuggestionType suggestionType;
    private String suggestionReason;
}
