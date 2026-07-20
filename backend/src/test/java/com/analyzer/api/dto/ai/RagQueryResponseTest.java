package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagQueryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCamelCaseAiServiceResponse() throws Exception {
        String json = """
                {
                  "requestId": "req-1",
                  "answer": "OK",
                  "confidenceScore": 0.91,
                  "shouldSuggestTicket": true,
                  "suggestionType": "SUGGEST_LAWYER",
                  "riskLevel": "HIGH",
                  "userActionHint": "CONTACT_LAWYER",
                  "model": "gemini-test",
                  "usage": {"promptTokens": 10, "completionTokens": 5, "totalTokens": 15},
                  "citations": [{
                    "citationId": "K1",
                    "sourceType": "SYSTEM_KB",
                    "knowledgeDocumentId": "law-1",
                    "lawName": "Bộ luật Dân sự",
                    "score": 0.88,
                    "excerpt": "Nội dung trích dẫn"
                  }]
                }
                """;

        RagQueryResponse response = objectMapper.readValue(json, RagQueryResponse.class);

        assertThat(response.getRequestId()).isEqualTo("req-1");
        assertThat(response.getConfidenceScore()).isEqualTo(0.91);
        assertThat(response.getSuggestionType()).isEqualTo(SuggestionType.SUGGEST_LAWYER);
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.getUserActionHint()).isEqualTo(UserActionHint.CONTACT_LAWYER);
        assertThat(response.getModel()).isEqualTo("gemini-test");
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(15);
        assertThat(response.getCitations()).singleElement()
                .satisfies(citation -> {
                    assertThat(citation.getCitationId()).isEqualTo("K1");
                    assertThat(citation.getExcerpt()).isEqualTo("Nội dung trích dẫn");
                });
    }
}
