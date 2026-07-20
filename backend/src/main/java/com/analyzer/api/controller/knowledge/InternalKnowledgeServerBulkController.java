package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.knowledge.Neo4jKnowledgeRepairRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;
import com.analyzer.api.service.knowledge.KnowledgeServerBulkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/internal/knowledge-bulk")
@RequiredArgsConstructor
public class InternalKnowledgeServerBulkController {
    private final KnowledgeServerBulkService service;

    @Value("${app.knowledge.bulk-internal-key:}")
    private String configuredKey;

    @Value("${app.knowledge.bulk-admin-id:}")
    private String configuredAdminId;

    @PostMapping("/ingest-file")
    public ResponseEntity<ApiResponseDTO<ServerBulkIngestFileResponse>> ingestFile(
            @RequestHeader("X-Internal-Bulk-Key") String key,
            @Valid @RequestBody ServerBulkIngestFileRequest request) {
        validateKey(key);
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Xu ly tai lieu bulk server thanh cong", service.ingestFile(request, adminId())));
    }

    @PostMapping("/repair-neo4j-document")
    public ResponseEntity<ApiResponseDTO<ServerBulkIngestFileResponse>> repairNeo4jDocument(
            @RequestHeader("X-Internal-Bulk-Key") String key,
            @Valid @RequestBody Neo4jKnowledgeRepairRequest request) {
        validateKey(key);
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Dong bo metadata Neo4j sang Postgres thanh cong", service.repairFromNeo4j(request, adminId())));
    }

    private void validateKey(String suppliedKey) {
        if (configuredKey == null || configuredKey.isBlank()
                || suppliedKey == null || !MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new org.springframework.security.access.AccessDeniedException("INVALID_BULK_INTERNAL_KEY");
        }
    }

    private Long adminId() {
        try {
            return Long.valueOf(configuredAdminId);
        } catch (Exception exception) {
            throw new IllegalStateException("KNOWLEDGE_BULK_ADMIN_ID_REQUIRED");
        }
    }
}
