package com.analyzer.api.dto.legalticket;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Request ID không được để trống")
    @JsonProperty("request_id")
    @Schema(description = "AI request id that triggered the ticket suggestion", example = "msg_abc123")
    private String requestId;

    @NotBlank(message = "Workspace ID không được để trống")
    @JsonProperty("workspace_id")
    @Schema(description = "Workspace identifier associated with the legal issue", example = "ws_xyz456")
    private String workspaceId;

    @JsonProperty("document_id")
    @Schema(description = "Optional document identifier related to the question", example = "doc_789")
    private String documentId;

    @JsonProperty("issue_fingerprint")
    @Schema(description = "Fingerprint of the specific clause issue to resolve", example = "hash_issue_001")
    private String issueFingerprint;

    @JsonProperty("customer_note")
    @Schema(description = "Note from the customer detailing their concern", example = "Tôi muốn chuyên gia rà soát kỹ điều khoản phạt vi phạm này.")
    private String customerNote;
}
