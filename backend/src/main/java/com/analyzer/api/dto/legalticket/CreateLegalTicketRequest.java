package com.analyzer.api.dto.legalticket;

import com.analyzer.api.enums.LegalTicketType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a legal ticket")
public class CreateLegalTicketRequest {

    @JsonProperty("ticket_type")
    private LegalTicketType ticketType;

    @JsonProperty("chat_session_id")
    private String chatSessionId;

    @JsonProperty("chat_message_id")
    private String chatMessageId;

    @JsonProperty("request_id")
    @Schema(description = "AI request id that triggered the ticket suggestion, if the ticket is created from AI chat", example = "msg_abc123")
    private String requestId;

    @NotBlank(message = "Workspace ID khong duoc de trong")
    @JsonProperty("workspace_id")
    @Schema(description = "Workspace identifier associated with the legal issue", example = "ws_xyz456")
    private String workspaceId;

    @JsonProperty("document_id")
    @Schema(description = "Optional document identifier related to the question", example = "doc_789")
    private String documentId;

    @NotBlank(message = "Cau hoi khong duoc de trong")
    @Size(max = 5000, message = "Cau hoi khong duoc vuot qua 5000 ky tu")
    @JsonProperty("question")
    @Schema(description = "Question or issue description submitted by the customer", example = "Hay xem giup toi dieu khoan phat huy hop dong nay.")
    private String question;

    @JsonProperty("issue_fingerprint")
    @Schema(description = "Fingerprint of the specific clause issue to resolve", example = "hash_issue_001")
    private String issueFingerprint;

    @JsonProperty("customer_note")
    @Schema(description = "Optional note from the customer", example = "Toi muon chuyen gia ra soat ky dieu khoan phat vi pham nay.")
    private String customerNote;
}
