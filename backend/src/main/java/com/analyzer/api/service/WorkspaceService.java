package com.analyzer.api.service;

import com.analyzer.api.dto.document.DocumentResponseDTO;
import com.analyzer.api.dto.document.ProcessingResultRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface WorkspaceService {
    WorkspaceResponseDTO createWorkspace(Long userId, WorkspaceRequestDTO request);
    List<WorkspaceResponseDTO> getWorkspaces(Long userId);
    WorkspaceResponseDTO getWorkspaceById(Long userId, String workspaceId);
    List<DocumentResponseDTO> getDocuments(Long userId, String workspaceId);
    DocumentResponseDTO uploadDocument(Long userId, String workspaceId, MultipartFile file) throws IOException;
    DocumentResponseDTO updateProcessingResult(String documentId, ProcessingResultRequestDTO request);
}
