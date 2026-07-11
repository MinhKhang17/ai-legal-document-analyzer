package com.analyzer.api.service.lawyer.impl;

import com.analyzer.api.dto.legalticket.TicketFileResponse;
import com.analyzer.api.dto.legalticket.UploadTicketFileRequest;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.DocumentPurpose;
import com.analyzer.api.enums.DocumentVisibilityScope;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.DocumentRepository;
import com.analyzer.api.repository.LegalTicketRepository;
import com.analyzer.api.repository.UserRepository;
import com.analyzer.api.service.lawyer.TicketFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketFileServiceImpl implements TicketFileService {

    private final LegalTicketRepository legalTicketRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Value("${app.storage.upload-root:uploads}")
    private String uploadRoot;

    @Override
    @Transactional(readOnly = true)
    public List<TicketFileResponse> listFiles(String ticketId, Long lawyerId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau tu van ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Ban khong phai la Luat su duoc phan cong xu ly yeu cau nay");
        }

        List<Document> documents = documentRepository.findByLegalTicket_Id(ticketId);

        return documents.stream()
                .map(doc -> TicketFileResponse.builder()
                        .documentId(doc.getId())
                        .originalFileName(doc.getOriginalFileName())
                        .storedFileName(doc.getStoredFileName())
                        .filePath(doc.getFilePath())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .documentPurpose(doc.getDocumentPurpose())
                        .visibilityScope(doc.getVisibilityScope())
                        .uploadedAt(doc.getUploadedAt() != null ? doc.getUploadedAt() : doc.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public TicketFileResponse uploadFile(String ticketId, Long lawyerId, UploadTicketFileRequest request) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau tu van ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Ban khong phai la Luat su duoc phan cong xu ly yeu cau nay");
        }

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay thong tin Luat su ID: " + lawyerId));

        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        DocumentVisibilityScope visibility = request.getVisibilityScope() != null
                ? request.getVisibilityScope()
                : DocumentVisibilityScope.ALL_INTERNAL;

        String originalFileName = request.getOriginalFileName().trim();
        String storedFileName = docId + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        byte[] fileContent = decodeBase64Content(request.getContentBase64());

        Path ticketDir = Path.of(uploadRoot, "lawyer_tickets", ticketId).toAbsolutePath().normalize();
        Path storedPath = ticketDir.resolve(storedFileName).normalize();

        try {
            Files.createDirectories(ticketDir);
            Files.write(storedPath, fileContent);
        } catch (IOException ex) {
            throw new RuntimeException("Khong the luu file tai lieu cua lawyer: " + ex.getMessage(), ex);
        }

        Document document = Document.builder()
                .id(docId)
                .workspace(ticket.getWorkspace())
                .user(lawyer)
                .legalTicket(ticket)
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .filePath(storedPath.toString())
                .fileType(request.getFileType().trim())
                .fileSize((long) fileContent.length)
                .status("READY")
                .sourceType("LAWYER_UPLOAD")
                .documentPurpose(DocumentPurpose.LAWYER_ATTACHMENT)
                .visibilityScope(visibility)
                .build();

        Document savedDoc = documentRepository.save(document);

        return TicketFileResponse.builder()
                .documentId(savedDoc.getId())
                .originalFileName(savedDoc.getOriginalFileName())
                .storedFileName(savedDoc.getStoredFileName())
                .filePath(savedDoc.getFilePath())
                .fileType(savedDoc.getFileType())
                .fileSize(savedDoc.getFileSize())
                .documentPurpose(savedDoc.getDocumentPurpose())
                .visibilityScope(savedDoc.getVisibilityScope())
                .uploadedAt(savedDoc.getUploadedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadFile(String ticketId, Long lawyerId, String documentId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau tu van ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Ban khong phai la Luat su duoc phan cong xu ly yeu cau nay");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay tai lieu ID: " + documentId));

        if (document.getLegalTicket() == null || !ticketId.equals(document.getLegalTicket().getId())) {
            throw new ForbiddenException("Tai lieu khong thuoc yeu cau tu van nay");
        }

        try {
            Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("Khong the doc file tai lieu: " + documentId);
        } catch (IOException ex) {
            throw new RuntimeException("Loi khi doc file tai lieu: " + ex.getMessage(), ex);
        }
    }

    private byte[] decodeBase64Content(String contentBase64) {
        String normalized = contentBase64 == null ? "" : contentBase64.trim();
        if (normalized.isEmpty()) {
            throw new RuntimeException("Noi dung file khong duoc de trong");
        }

        int commaIndex = normalized.indexOf(',');
        if (normalized.startsWith("data:") && commaIndex >= 0) {
            normalized = normalized.substring(commaIndex + 1);
        }

        try {
            return Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Noi dung file base64 khong hop le", ex);
        }
    }
}
