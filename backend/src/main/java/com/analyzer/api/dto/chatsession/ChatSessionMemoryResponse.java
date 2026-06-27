package com.analyzer.api.dto.chatsession;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionMemoryResponse {

    private String chatSessionId;
    private String memoryJson;
    private String contextJson;
    private Long contextVersion;
}
