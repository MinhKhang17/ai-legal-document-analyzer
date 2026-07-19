package com.analyzer.api.dto.legalticket;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketContextSnapshotResponse {
    private String id;
    private String userQuestion;
    private String assistantAnswer;
    private String conversationTitle;
    private String citationSnapshotJson;
    private String documentSnapshotJson;
    private String selectedMessageSnapshotJson;
    private String contentHash;
    private LocalDateTime createdAt;
}
