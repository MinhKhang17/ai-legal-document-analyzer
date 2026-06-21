package com.analyzer.api.service.impl;

import com.analyzer.api.dto.document.DocumentResponseDTO;
import com.analyzer.api.dto.document.ProcessDocumentRequestDTO;
import com.analyzer.api.dto.document.ProcessingResultRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceRequestDTO;
import com.analyzer.api.dto.workspace.WorkspaceResponseDTO;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.exception.workspace.DocumentProcessingDispatchException;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import com.analyzer.api.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

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
                .user(User.builder().id(userId).build())
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(STATUS_ACTIVE)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return toWorkspaceResponse(savedWorkspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceResponseDTO> getWorkspaces(Long userId) {
        return workspaceRepository
                .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, STATUS_ACTIVE)
                .stream()
                .map(this::toWorkspaceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponseDTO getWorkspaceById(Long userId, String workspaceId) {
        Workspace workspace = workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));
        return toWorkspaceResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponseDTO> getDocuments(Long userId, String workspaceId) {
        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        return documentRepository
                .findByWorkspaceIdAndUserIdAndStatusNotOrderByUploadedAtDesc(workspaceId, userId, STATUS_DELETED)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Override
    @Transactional(noRollbackFor = DocumentProcessingDispatchException.class)
    public DocumentResponseDTO uploadDocument(Long userId, String workspaceId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File khong duoc de trong");
        }

        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        String documentId = generateDocumentId();
        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String storedFileName = documentId + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path documentDir = Path.of(uploadRoot)
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
                .workspace(Workspace.builder().id(workspaceId).build())
                .user(User.builder().id(userId).build())
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
                .workspaceId(document.getWorkspace().getId())
                .userId(String.valueOf(document.getUser().getId()))
                .sourceType(document.getSourceType())
                .fileName(document.getOriginalFileName())
                .fileType(resolveDocumentFileType(document.getOriginalFileName(), document.getFileType()))
                .filePath(document.getFilePath())
                .callbackUrl(callbackBaseUrl + "/api/internal/documents/" + document.getId() + "/processing-result")
                .build();

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());

            ResponseEntity<String> response = new RestTemplate().exchange(
                    URI.create(aiServiceBaseUrl + "/internal/documents/process"),
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("AI service rejected document processing request. status={} body={} request={}",
                        response.getStatusCode().value(),
                        response.getBody(),
                        requestBody);
                document.setStatus(STATUS_FAILED);
                document.setChunkCount(0);
                document.setErrorMessage("Python AI Service returned HTTP " + response.getStatusCode().value()
                        + (response.getBody() == null || response.getBody().isBlank() ? "" : ": " + response.getBody()));
                documentRepository.save(document);
                throw new DocumentProcessingDispatchException("Python AI Service returned HTTP " + response.getStatusCode().value());
            }
            document.setStatus(STATUS_PROCESSING);
            document.setErrorMessage(null);
            document.setProcessedAt(null);
        } catch (RestClientException ex) {
            document.setStatus(STATUS_FAILED);
            document.setChunkCount(0);
            document.setErrorMessage("Cannot call Python AI Service: " + ex.getMessage());
            documentRepository.save(document);
            throw new DocumentProcessingDispatchException("Cannot call Python AI Service: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            if (ex instanceof DocumentProcessingDispatchException dispatchException) {
                throw dispatchException;
            }
            document.setStatus(STATUS_FAILED);
            document.setChunkCount(0);
            document.setErrorMessage("Cannot call Python AI Service: " + ex.getMessage());
            documentRepository.save(document);
            throw new DocumentProcessingDispatchException("Cannot call Python AI Service: " + ex.getMessage(), ex);
        }
        return documentRepository.save(document);
    }

    private String resolveDocumentFileType(String fileName, String contentType) {
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase();
        if (normalizedFileName.endsWith(".pdf")) {
            return "pdf";
        }
        if (normalizedFileName.endsWith(".doc")) {
            return "doc";
        }
        if (normalizedFileName.endsWith(".docx")) {
            return "docx";
        }
        if (contentType != null) {
            String normalizedContentType = contentType.toLowerCase();
            if (normalizedContentType.contains("pdf")) {
                return "pdf";
            }
            if (normalizedContentType.contains("msword")) {
                return "doc";
            }
            if (normalizedContentType.contains("wordprocessingml.document") || normalizedContentType.contains("docx")) {
                return "docx";
            }
        }
        return normalizedFileName.endsWith(".doc") ? "doc" : "";
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
                .workspaceId(document.getWorkspace().getId())
                .originalFileName(document.getOriginalFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .uploadedAt(document.getUploadedAt())
                .build();
    }
}
