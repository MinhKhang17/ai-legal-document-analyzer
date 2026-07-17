package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionJobResponse;
import com.analyzer.api.dto.knowledge.KnowledgeIngestionProgressRequest;
import com.analyzer.api.service.knowledge.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/knowledge-ingestion")
@RequiredArgsConstructor
public class InternalKnowledgeIngestionController {

    private final KnowledgeIngestionService knowledgeIngestionService;

    @PostMapping("/{jobId}/progress")
    public ResponseEntity<ApiResponseDTO<KnowledgeIngestionJobResponse>> updateProgress(
            @PathVariable String jobId,
            @RequestBody KnowledgeIngestionProgressRequest request) {
        return ResponseEntity.ok(ApiResponseDTO.success("Cap nhat ingest progress thanh cong",
                knowledgeIngestionService.updateProgress(jobId, request)));
    }
}
