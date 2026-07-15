package com.analyzer.api.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseIngestedDocumentResponse {

    private String legalDocumentId;
    private String title;
    private String documentCode;
    private List<KnowledgeBaseIngestedDocumentVersionResponse> versions;
}
