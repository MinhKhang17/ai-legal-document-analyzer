package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExpertAssignmentDecisionRequest {
    @NotBlank @Pattern(regexp = "ACCEPT|DECLINE") private String decision;
    @Size(max = 2000) private String reason;
}
