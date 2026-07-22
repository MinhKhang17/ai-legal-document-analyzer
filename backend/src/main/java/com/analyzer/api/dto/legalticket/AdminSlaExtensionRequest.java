package com.analyzer.api.dto.legalticket;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminSlaExtensionRequest {
    @Min(1) @Max(720) private int hours;
    @NotBlank @Size(max = 2000) private String reason;
}
