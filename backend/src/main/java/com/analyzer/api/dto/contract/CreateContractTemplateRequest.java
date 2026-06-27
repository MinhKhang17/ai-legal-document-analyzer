package com.analyzer.api.dto.contract;

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
public class CreateContractTemplateRequest {

    @NotBlank
    @Size(max = 100)
    private String templateCode;

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 255)
    private String category;

    @Size(max = 255)
    private String jurisdiction;

    @NotBlank
    private String content;
}
