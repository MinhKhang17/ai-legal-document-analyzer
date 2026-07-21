package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.LegalTicketMessageType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response representing a ticket message thread item")
public class LegalTicketMessageResponse {

    @JsonProperty("id")
    private String id;
    private String clientMessageId;

    @JsonProperty("ticket_id")
    private String ticketId;

    @JsonProperty("sender_id")
    private Long senderId;

    @JsonProperty("sender_name")
    private String senderName;

    @JsonProperty("sender_role")
    private String senderRole;

    private String replyToMessageId;

    @Builder.Default
    private List<TicketAttachmentResponse> attachments = List.of();

    @JsonProperty("content")
    private String content;

    @JsonProperty("message_type")
    private LegalTicketMessageType messageType;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private LocalDateTime editedAt;

    @JsonProperty("internal_only")
    private Boolean internalOnly;
}
