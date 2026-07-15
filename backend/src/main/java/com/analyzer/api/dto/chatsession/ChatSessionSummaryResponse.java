package com.analyzer.api.dto.chatsession;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummaryResponse {

    private String chatSessionId;
    private String summary;
    private LocalDateTime summaryUpdatedAt;
}
