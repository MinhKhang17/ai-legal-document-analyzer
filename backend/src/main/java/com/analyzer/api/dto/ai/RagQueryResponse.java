package com.analyzer.api.dto.ai;

import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.enums.SuggestionType;
import com.analyzer.api.enums.UserActionHint;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagQueryResponse {
    @JsonProperty("request_id")
    @JsonAlias("requestId")
    private String requestId;
    @JsonProperty("success")
    private Boolean success;
    @JsonProperty("answer")
    private String answer;
    @JsonProperty("confidence_score")
    @JsonAlias("confidenceScore")
    private Double confidenceScore;
    @JsonProperty("should_suggest_ticket")
    @JsonAlias("shouldSuggestTicket")
    private Boolean shouldSuggestTicket;
    @JsonProperty("suggestion_type")
    @JsonAlias("suggestionType")
    private SuggestionType suggestionType;
    @JsonProperty("suggestion_reason")
    @JsonAlias("suggestionReason")
    private String suggestionReason;
    @JsonProperty("missing_information")
    @JsonAlias("missingInformation")
    private String missingInformation;
    @JsonProperty("risk_level")
    @JsonAlias("riskLevel")
    private RiskLevel riskLevel;
    @JsonProperty("legal_domain")
    @JsonAlias("legalDomain")
    private String legalDomain;
    @JsonProperty("user_action_hint")
    @JsonAlias("userActionHint")
    private UserActionHint userActionHint;
    @JsonProperty("citations")
    private List<Citation> citations;
    @JsonProperty("used_knowledge_citation_ids")
    @JsonAlias("usedKnowledgeCitationIds")
    private List<String> usedKnowledgeCitationIds;
    @JsonProperty("used_user_citation_ids")
    @JsonAlias("usedUserCitationIds")
    private List<String> usedUserCitationIds;
    @JsonProperty("usage")
    private Usage usage;
    @JsonProperty("model")
    private String model;
    @JsonProperty("error_message")
    private String errorMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        @JsonAlias("promptTokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        @JsonAlias("completionTokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        @JsonAlias("totalTokens")
        private Integer totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Citation {
        @JsonAlias("citation_id")
        private String citationId;
        @JsonAlias("source_type")
        private String sourceType;
        private Double score;
        @JsonAlias("document_id")
        private String documentId;
        @JsonAlias("workspace_id")
        private String workspaceId;
        @JsonAlias("user_id")
        private String userId;
        @JsonAlias("file_name")
        private String fileName;
        @JsonAlias("knowledge_document_id")
        private String knowledgeDocumentId;
        @JsonAlias("law_name")
        private String lawName;
        @JsonAlias("law_code")
        private String lawCode;
        @JsonAlias("page_number")
        private Integer pageNumber;
        @JsonAlias("section_title")
        private String sectionTitle;
        private String excerpt;
    }
}
