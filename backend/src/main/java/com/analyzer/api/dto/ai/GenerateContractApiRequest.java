package com.analyzer.api.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateContractApiRequest {
    @NotBlank(message = "requestId khong duoc de trong")
    private String requestId;

    @NotBlank(message = "templateContent khong duoc de trong")
    private String templateContent;

    @NotBlank(message = "inputJson khong duoc de trong")
    private String inputJson;
}
