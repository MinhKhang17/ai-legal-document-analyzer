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
public class RevertContractVersionRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;
}
