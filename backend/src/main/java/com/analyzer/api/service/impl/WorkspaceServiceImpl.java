package com.analyzer.api.service.impl;

import com.analyzer.api.dto.document.DocumentResponseDTO;
import com.analyzer.api.dto.document.ProcessDocumentRequestDTO;
import com.analyzer.api.dto.document.ProcessingResultRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceResponseDTO;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DELETED = "DELETED";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_TYPE_USER_DOCUMENT = "USER_DOCUMENT";

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.storage.upload-root:uploads}")
    private String uploadRoot;

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    @Value("${app.api.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Override
    @Transactional
    public WorkspaceResponseDTO createWorkspace(Long userId, WorkspaceRequestDTO request) {
        Workspace workspace = Workspace.builder()
                .id(generateWorkspaceId())
                .userId(String.valueOf(userId))
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(STATUS_ACTIVE)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return toWorkspaceResponse(savedWorkspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getDocuments(Long userId, String workspaceId) {
        String currentUserId = String.valueOf(userId);
        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, currentUserId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        return documentRepository
                .findByWorkspaceIdAndUserIdAndStatusNotOrderByUploadedAtDesc(workspaceId, currentUserId, STATUS_DELETED)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    @Transactional
    public DocumentResponseDTO uploadDocument(Long userId, String workspaceId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File khong duoc de trong");
        }

        String currentUserId = String.valueOf(userId);
        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, currentUserId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        String documentId = generateDocumentId();
        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String storedFileName = documentId + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path documentDir = Path.of(uploadRoot, "users", currentUserId, "workspaces", workspaceId, "documents", documentId)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(documentDir);
        Path storedPath = documentDir.resolve(storedFileName).normalize();
        if (!storedPath.startsWith(documentDir)) {
            throw new RuntimeException("Ten file khong hop le");
        }
        Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

        Document document = Document.builder()
                .id(documentId)
                .workspaceId(workspaceId)
                .userId(currentUserId)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .filePath(storedPath.toString())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .sourceType(SOURCE_TYPE_USER_DOCUMENT)
                .status(STATUS_UPLOADED)
                .chunkCount(0)
                .build();

        Document savedDocument = documentRepository.save(document);
        return toDocumentResponse(sendProcessingRequest(savedDocument));
    }

    @Override
    @Transactional
    public DocumentResponseDTO updateProcessingResult(String documentId, ProcessingResultRequestDTO request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay document voi id: " + documentId));

        if (STATUS_READY.equalsIgnoreCase(request.getStatus())) {
            document.setStatus(STATUS_READY);
            document.setChunkCount(request.getChunkCount() == null ? 0 : request.getChunkCount());
            document.setErrorMessage(null);
            document.setProcessedAt(LocalDateTime.now());
        } else if (STATUS_FAILED.equalsIgnoreCase(request.getStatus())) {
            document.setStatus(STATUS_FAILED);
            document.setChunkCount(request.getChunkCount() == null ? 0 : request.getChunkCount());
            document.setErrorMessage(request.getErrorMessage());
        } else {
            throw new RuntimeException("Trang thai xu ly khong hop le: " + request.getStatus());
        }

        return toDocumentResponse(documentRepository.save(document));
    }

    private Document sendProcessingRequest(Document document) {
        String jobId = "job_" + UUID.randomUUID().toString().replace("-", "");
        ProcessDocumentRequestDTO request = ProcessDocumentRequestDTO.builder()
                .jobId(jobId)
                .documentId(document.getId())
                .workspaceId(document.getWorkspaceId())
                .userId(document.getUserId())
                .sourceType(document.getSourceType())
                .fileName(document.getOriginalFileName())
                .fileType(document.getFileType())
                .filePath(document.getFilePath())
                .callbackUrl(callbackBaseUrl + "/api/internal/documents/" + document.getId() + "/processing-result")
                .build();

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceBaseUrl + "/internal/documents/process"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Python AI Service returned HTTP " + response.statusCode());
            }
            document.setStatus(STATUS_PROCESSING);
            document.setErrorMessage(null);
        } catch (Exception ex) {
            document.setStatus(STATUS_FAILED);
            document.setChunkCount(0);
            document.setErrorMessage("Cannot call Python AI Service: " + ex.getMessage());
        }
        return documentRepository.save(document);
    }

    private String generateWorkspaceId() {
        return "ws_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateDocumentId() {
        return "doc_" + UUID.randomUUID().toString().replace("-", "");
    }

    private WorkspaceResponseDTO toWorkspaceResponse(Workspace workspace) {
        return WorkspaceResponseDTO.builder()
                .workspaceId(workspace.getId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .status(workspace.getStatus())
                .createdAt(workspace.getCreatedAt())
                .build();
    }

    private DocumentResponseDTO toDocumentResponse(Document document) {
        return DocumentResponseDTO.builder()
                .documentId(document.getId())
                .workspaceId(document.getWorkspaceId())
                .originalFileName(document.getOriginalFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
