package com.analyzer.api.service.impl;

import com.analyzer.api.dto.document.DocumentResponse;
import com.analyzer.api.dto.document.ProcessDocumentRequest;
import com.analyzer.api.dto.document.ProcessingResultRequest;
import com.analyzer.api.dto.workspace.WorkspaceRequest;
import com.analyzer.api.dto.workspace.WorkspaceResponse;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.User;
import com.analyzer.api.entity.Workspace;
import com.analyzer.api.enums.RoleName;
import com.analyzer.api.enums.KnowledgeStatus;
import com.analyzer.api.enums.KnowledgeVisibility;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.exception.workspace.DocumentProcessingDispatchException;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.repository.knowledge.KnowledgeBaseVersionRepository;
import com.analyzer.api.service.EmailService;
import com.analyzer.api.service.SubscriptionQuotaService;
import com.analyzer.api.service.PolicyAcceptanceService;
import com.analyzer.api.service.support.DocumentUploadValidator;
import com.analyzer.api.service.WorkspaceService;
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
import org.springframework.web.client.HttpStatusCodeException;
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
    private static final String SYSTEM_SANDBOX_NAME = "Contract Assistant Sandbox";
    private static final String SYSTEM_SANDBOX_DESCRIPTION = "System workspace for general contract assistant chat";

    private final WorkspaceRepository workspaceRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final SubscriptionQuotaService subscriptionQuotaService;
    private final EmailService emailService;
    private final PolicyAcceptanceService policyAcceptanceService;
    private final KnowledgeBaseVersionRepository knowledgeBaseVersionRepository;

    @Value("${app.storage.upload-root:uploads}")
    private String uploadRoot;

    @Value("${app.ai-service.base-url:http://localhost:8000}")
    private String aiServiceBaseUrl;

    @Value("${app.api.callback-base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(Long userId, WorkspaceRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND"));
        String normalizedName = request.name().trim();
        if (workspaceRepository.existsByUserIdAndNameIgnoreCaseAndStatus(userId, normalizedName, STATUS_ACTIVE)) {
            throw new com.analyzer.api.exception.common.ConflictException(
                    "WORKSPACE_ALREADY_EXISTS", "An active workspace with this name already exists");
        }
        if (isSystemSandbox(normalizedName, request.description())) {
            subscriptionQuotaService.getCurrentPlan(user);
        } else {
            subscriptionQuotaService.checkCanCreateWorkspace(user);
        }

        Workspace workspace = Workspace.builder()
                .id(generateWorkspaceId())
                .user(user)
                .name(normalizedName)
                .description(request.description())
                .status(STATUS_ACTIVE)
                .build();

        Workspace savedWorkspace = workspaceRepository.save(workspace);
        return toWorkspaceResponse(savedWorkspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getWorkspaces(Long userId) {
        return workspaceRepository
                .findAllByUserIdAndStatusOrderByCreatedAtDesc(userId, STATUS_ACTIVE)
                .stream()
                .map(this::toWorkspaceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceById(Long userId, String workspaceId) {
        Workspace workspace = workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));
        return toWorkspaceResponse(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(Long userId, String workspaceId) {
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
    public DocumentResponse uploadDocument(Long userId, String workspaceId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File khong duoc de trong");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung"));
        policyAcceptanceService.requireCurrent(userId);
        subscriptionQuotaService.checkCanUploadOrAnalyzeContract(user, workspaceId, file.getSize());

        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        DocumentUploadValidator.validate(file);

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
    public DocumentResponse updateProcessingResult(String documentId, ProcessingResultRequest request) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay document voi id: " + documentId));

        if (STATUS_READY.equalsIgnoreCase(request.getStatus())) {
            document.setStatus(STATUS_READY);
            document.setChunkCount(request.getChunkCount() == null ? 0 : request.getChunkCount());
            document.setErrorMessage(null);
            document.setProcessedAt(LocalDateTime.now());
            emailService.sendIngestionSuccessEmailAsync(
                    document.getUser().getEmail(),
                    document.getUser().getFirstName(),
                    document.getOriginalFileName());
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
        ProcessDocumentRequest request = ProcessDocumentRequest.builder()
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());

            ResponseEntity<String> response = new RestTemplate().exchange(
                    URI.create(aiServiceBaseUrl + "/internal/documents/process"),
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.warn("AI service rejected document processing request. status={} body={} request={}",
                        response.getStatusCode().value(),
                        response.getBody(),
                        request);
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
        } catch (HttpStatusCodeException ex) {
            String responseBody = ex.getResponseBodyAsString();
            logger.warn("AI service rejected document processing request. status={} body={} request={}",
                    ex.getStatusCode().value(),
                    responseBody,
                    request);
            document.setStatus(STATUS_FAILED);
            document.setChunkCount(0);
            document.setErrorMessage("Python AI Service returned HTTP " + ex.getStatusCode().value()
                    + (responseBody == null || responseBody.isBlank() ? "" : ": " + responseBody));
            documentRepository.save(document);
            throw new DocumentProcessingDispatchException("Python AI Service returned HTTP " + ex.getStatusCode().value()
                    + (responseBody == null || responseBody.isBlank() ? "" : ": " + responseBody), ex);
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

    private boolean isSystemSandbox(String name, String description) {
        return SYSTEM_SANDBOX_NAME.equals(name)
                && SYSTEM_SANDBOX_DESCRIPTION.equals(description == null ? null : description.trim());
    }

    private String generateDocumentId() {
        return "doc_" + UUID.randomUUID().toString().replace("-", "");
    }

    private WorkspaceResponse toWorkspaceResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getStatus(),
                workspace.getCreatedAt()
        );
    }

    private DocumentResponse toDocumentResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getWorkspace().getId(),
                document.getOriginalFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getStatus(),
                document.getUploadedAt(),
                document.getErrorMessage(),
                document.getContractType() == null ? null : document.getContractType().name(),
                document.getContractTypeConfirmed()
        );
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.core.io.Resource downloadDocumentFile(Long userId, String workspaceId, String documentId) {
        workspaceRepository.findByIdAndUserIdAndStatus(workspaceId, userId, STATUS_ACTIVE)
                .orElseThrow(() -> new RuntimeException("Workspace khong ton tai hoac khong thuoc user hien tai"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay document voi id: " + documentId));

        if (!document.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Document khong thuoc workspace nay");
        }
        if (document.getUser() == null || !userId.equals(document.getUser().getId())) {
            throw new ForbiddenException("DOCUMENT_ACCESS_DENIED");
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Path.of(document.getFilePath()).toAbsolutePath().normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File khong ton tai hoac khong the doc");
            }
        } catch (Exception e) {
            throw new RuntimeException("Loi khi doc file: " + e.getMessage(), e);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.core.io.Resource downloadDocumentFilePublic(String workspaceId, String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay document voi id: " + documentId));

        if (!document.getWorkspace().getId().equals(workspaceId)) {
            throw new RuntimeException("Document khong thuoc workspace nay");
        }

        try {
            java.nio.file.Path filePath = java.nio.file.Path.of(document.getFilePath()).toAbsolutePath().normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File khong ton tai hoac khong the doc");
            }
        } catch (Exception e) {
            throw new RuntimeException("Loi khi doc file: " + e.getMessage(), e);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.core.io.Resource downloadDocumentForStaff(Long currentUserId, String currentUserRole, String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay tai lieu voi id: " + documentId));

        if (RoleName.EXPERT.name().equalsIgnoreCase(currentUserRole)) {
            boolean assignedToCurrentExpert = document.getLegalTicket() != null
                    && document.getLegalTicket().getAssignedLawyer() != null
                    && document.getLegalTicket().getAssignedLawyer().getId().equals(currentUserId);
            if (!assignedToCurrentExpert) {
                throw new ForbiddenException("Ban khong duoc phan cong xu ly yeu cau tu van chua tai lieu nay");
            }
        }

        try {
            Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
            if (!Files.exists(filePath)) {
                throw new ResourceNotFoundException("File vat ly cua tai lieu khong con ton tai tren server");
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (!resource.isReadable()) {
                throw new ResourceNotFoundException("Khong the doc file tai lieu");
            }
            return resource;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Loi khi doc file: " + e.getMessage(), e);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public DocumentResponse registerGeneratedDocument(com.analyzer.api.dto.document.RegisterDocumentRequest request) {
        String documentId = generateDocumentId();
        Document document = Document.builder()
                .id(documentId)
                .workspace(Workspace.builder().id(request.getWorkspaceId()).build())
                .user(User.builder().id(Long.valueOf(request.getUserId())).build())
                .originalFileName(request.getOriginalFileName())
                .storedFileName(request.getStoredFileName())
                .filePath(request.getFilePath())
                .fileType(request.getOriginalFileName().endsWith(".docx")
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : "application/pdf")
                .fileSize(request.getFileSize())
                .sourceType(SOURCE_TYPE_USER_DOCUMENT)
                .status(STATUS_READY)
                .chunkCount(0)
                .processedAt(LocalDateTime.now())
                .build();

        Document saved = documentRepository.save(document);
        return toDocumentResponse(saved);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.core.io.Resource downloadSystemDocumentFile(String filename) {
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("INVALID_KNOWLEDGE_FILENAME");
        }

        try {
            String targetName = filename.trim().toLowerCase(java.util.Locale.ROOT);
            com.analyzer.api.entity.KnowledgeBaseVersion version = knowledgeBaseVersionRepository
                    .findByStatusAndVisibilityAndActiveTrueOrderByCreatedAtDesc(
                            KnowledgeStatus.PUBLIC, KnowledgeVisibility.PUBLIC)
                    .stream()
                    .filter(candidate -> matchesKnowledgeFilename(candidate.getOriginalFileName(), targetName))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_SOURCE_FILE_NOT_FOUND"));

            String storedPath = version.getSourceStoragePath();
            if (!StringUtils.hasText(storedPath) && version.getSourceDocument() != null) {
                storedPath = version.getSourceDocument().getFilePath();
            }
            if (!StringUtils.hasText(storedPath)) {
                throw new ResourceNotFoundException("KNOWLEDGE_SOURCE_FILE_NOT_FOUND");
            }

            java.nio.file.Path baseDir = java.nio.file.Path.of(uploadRoot).toAbsolutePath().normalize();
            java.nio.file.Path filePath = java.nio.file.Path.of(storedPath).toAbsolutePath().normalize();
            String originalFileName = version.getOriginalFileName();
            if (Files.isDirectory(filePath) && StringUtils.hasText(originalFileName)) {
                String safeOriginalName = Path.of(originalFileName).getFileName().toString();
                filePath = filePath.resolve(safeOriginalName).normalize();
            }

            // Compatibility for knowledge records imported before source files
            // were stored under knowledge-source/<entry>/<version>/.
            if (!Files.isRegularFile(filePath) && StringUtils.hasText(originalFileName)) {
                String safeOriginalName = Path.of(originalFileName).getFileName().toString();
                List<Path> legacyCandidates = List.of(
                        baseDir.resolve("knowledge_base").resolve(safeOriginalName).normalize(),
                        baseDir.resolve(safeOriginalName).normalize());
                filePath = legacyCandidates.stream()
                        .filter(candidate -> candidate.startsWith(baseDir))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElse(filePath);
            }
            if (!filePath.startsWith(baseDir)) {
                throw new ForbiddenException("INVALID_KNOWLEDGE_SOURCE_PATH");
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            if (Files.isRegularFile(filePath) && resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("KNOWLEDGE_SOURCE_FILE_NOT_FOUND");
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("INVALID_KNOWLEDGE_SOURCE_URL", e);
        }
    }

    private boolean matchesKnowledgeFilename(String originalFileName, String targetName) {
        if (!StringUtils.hasText(originalFileName)) return false;
        String candidateName = originalFileName.trim().toLowerCase(java.util.Locale.ROOT);
        int lastDot = candidateName.lastIndexOf('.');
        String candidateStem = lastDot > 0 ? candidateName.substring(0, lastDot) : candidateName;
        return candidateName.equals(targetName) || candidateStem.equals(targetName);
    }

    @Override
    @Transactional
    public void softDeleteDocument(Long userId, String workspaceId, String documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài liệu không tồn tại với ID: " + documentId));

        if (!document.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Bạn không có quyền xóa tài liệu này");
        }

        if (!document.getWorkspace().getId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Tài liệu không thuộc về dự án này");
        }

        document.setStatus(STATUS_DELETED);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }
}
