package com.analyzer.api.dto.legalticket;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to assign a lawyer to a ticket")
public class AssignLawyerRequest {

    @NotNull(message = "ID luật sư/chuyên gia không được để trống")
    @JsonProperty("lawyer_id")
    @Schema(description = "ID of the expert/lawyer to assign", example = "3")
    private Long lawyerId;

    @JsonProperty("admin_note")
    @Schema(description = "Note from the admin regarding this assignment", example = "Chuyên gia này chuyên về luật thương mại.")
    private String adminNote;

    @JsonProperty("force_reassign")
    @Builder.Default
    @Schema(description = "Force re-assignment even if another expert is already working on the ticket", example = "false")
    private Boolean forceReassign = false;
}
