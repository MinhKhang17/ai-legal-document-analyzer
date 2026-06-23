package com.analyzer.api.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagQueryResponse {
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("success")
    private Boolean success;
    @JsonProperty("answer")
    private String answer;
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
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
