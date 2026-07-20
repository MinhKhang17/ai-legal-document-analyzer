package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.ConversationScope;
import jakarta.validation.constraints.Future;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateConversationShareRequest {
    private ConversationScope scope;
    @Future private LocalDateTime expiresAt;
}
