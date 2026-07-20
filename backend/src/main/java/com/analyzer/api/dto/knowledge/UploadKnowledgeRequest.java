package com.analyzer.api.dto.knowledge;

import com.analyzer.api.enums.KnowledgeScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadKnowledgeRequest {

    @NotBlank
    @Size(max = 100)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 255)
    private String category;

    @NotNull
    private KnowledgeScope scope;

    @NotNull
    private Long createdById;

    private Long workspaceId;

    private String extractedContent;

    private String rawContent;

    @Size(max = 500)
    private String description;
}
