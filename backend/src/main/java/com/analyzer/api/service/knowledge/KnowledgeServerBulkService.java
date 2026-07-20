package com.analyzer.api.service.knowledge;

import com.analyzer.api.dto.knowledge.Neo4jKnowledgeRepairRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileRequest;
import com.analyzer.api.dto.knowledge.ServerBulkIngestFileResponse;

public interface KnowledgeServerBulkService {
    ServerBulkIngestFileResponse ingestFile(ServerBulkIngestFileRequest request, Long adminId);
    ServerBulkIngestFileResponse repairFromNeo4j(Neo4jKnowledgeRepairRequest request, Long adminId);
}
