package com.analyzer.api.service.workspace;

import com.analyzer.api.dto.document.DocumentResponse;
import com.analyzer.api.dto.document.ProcessingResultRequest;
import com.analyzer.api.dto.workspace.WorkspaceRequest;
import com.analyzer.api.dto.workspace.WorkspaceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface WorkspaceService {
    WorkspaceResponse createWorkspace(Long userId, WorkspaceRequest request);
    List<WorkspaceResponse> getWorkspaces(Long userId);
    WorkspaceResponse getWorkspaceById(Long userId, String workspaceId);
    List<DocumentResponse> getDocuments(Long userId, String workspaceId);
    DocumentResponse uploadDocument(Long userId, String workspaceId, MultipartFile file) throws IOException;
    DocumentResponse updateProcessingResult(String documentId, ProcessingResultRequest request);
    org.springframework.core.io.Resource downloadDocumentFile(Long userId, String workspaceId, String documentId);
    org.springframework.core.io.Resource downloadDocumentFilePublic(String workspaceId, String documentId);
    DocumentResponse registerGeneratedDocument(com.analyzer.api.dto.document.RegisterDocumentRequest request);
    org.springframework.core.io.Resource downloadSystemDocumentFile(String filename);
    org.springframework.core.io.Resource downloadDocumentForStaff(Long currentUserId, String currentUserRole, String documentId);
    void softDeleteDocument(Long userId, String workspaceId, String documentId);
}
