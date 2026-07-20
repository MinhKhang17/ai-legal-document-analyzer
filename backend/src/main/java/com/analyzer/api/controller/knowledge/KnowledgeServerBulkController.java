package com.analyzer.api.controller.knowledge;

import com.analyzer.api.dto.ApiResponseDTO;
import com.analyzer.api.dto.knowledge.Neo4jKnowledgeRepairRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;
import com.analyzer.api.security.UserDetailsImpl;
import com.analyzer.api.service.knowledge.KnowledgeServerBulkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/knowledge-base")
@RequiredArgsConstructor
public class KnowledgeServerBulkController {
    private final KnowledgeServerBulkService service;

    @Value("${app.knowledge.bulk-admin-id:}")
    private String configuredAdminId;

    @PostMapping("/bulk-ingest-server-file")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponseDTO<ServerBulkIngestFileResponse>> ingestServerFile(
            @Valid @RequestBody ServerBulkIngestFileRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Xu ly tai lieu bulk server thanh cong", service.ingestFile(request, adminId(authentication))));
    }

    @PostMapping("/repair-neo4j-document")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<ServerBulkIngestFileResponse>> repairNeo4jDocument(
            @Valid @RequestBody Neo4jKnowledgeRepairRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Dong bo metadata Neo4j sang Postgres thanh cong", service.repairFromNeo4j(request, adminId(authentication))));
    }

    private Long adminId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl details) {
            return details.getId();
        }
        try {
            return Long.valueOf(configuredAdminId);
        } catch (Exception exception) {
            throw new IllegalStateException("KNOWLEDGE_BULK_ADMIN_ID_REQUIRED");
        }
    }
}
