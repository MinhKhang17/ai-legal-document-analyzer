package com.analyzer.api.service.lawyer.impl;

import com.analyzer.api.dto.legalticket.TicketFileResponse;
import com.analyzer.api.dto.legalticket.UploadTicketFileRequest;
import com.analyzer.api.entity.Document;
import com.analyzer.api.entity.LegalTicket;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.DocumentPurpose;
import com.analyzer.api.enums.DocumentVisibilityScope;
import com.analyzer.api.enums.LegalTicketStatus;
import com.analyzer.api.exception.common.ConflictException;
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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class TicketFileServiceImpl implements TicketFileService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp", "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final List<LegalTicketStatus> FILE_UPLOAD_STATUSES = List.of(
            LegalTicketStatus.ASSIGNED_TO_LAWYER,
            LegalTicketStatus.IN_REVIEW,
            LegalTicketStatus.NEED_MORE_INFO,
            LegalTicketStatus.CUSTOMER_RESPONDED,
            LegalTicketStatus.REOPENED);

    private final LegalTicketRepository legalTicketRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Value("${app.storage.upload-root:uploads}")
    private String uploadRoot;

    @Value("${app.ticket-files.max-size-mb:5}")
    private long maxFileSizeMb;

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
        if (!FILE_UPLOAD_STATUSES.contains(ticket.getStatus())) {
            throw new ConflictException("INVALID_STATUS_TRANSITION");
        }

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay thong tin Luat su ID: " + lawyerId));

        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        DocumentVisibilityScope visibility = request.getVisibilityScope() != null
                ? request.getVisibilityScope()
                : DocumentVisibilityScope.CUSTOMER;

        String originalFileName = request.getOriginalFileName().trim();
        String storedFileName = docId + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        byte[] fileContent = decodeBase64Content(request.getContentBase64());
        validateFileContent(originalFileName, request.getFileType(), fileContent);

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

        Document savedDoc;
        try {
            savedDoc = documentRepository.save(document);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(storedPath);
            } catch (IOException cleanupException) {
                ex.addSuppressed(cleanupException);
            }
            throw ex;
        }

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

    @Override
    @Transactional(readOnly = true)
    public List<TicketFileResponse> listCustomerVisibleFiles(String ticketId, Long customerId) {
        LegalTicket ticket = getOwnedTicket(ticketId, customerId);
        return documentRepository.findByLegalTicket_Id(ticket.getId()).stream()
                .filter(this::isCustomerVisible)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadCustomerVisibleFile(String ticketId, Long customerId, String documentId) {
        getOwnedTicket(ticketId, customerId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay tai lieu ID: " + documentId));
        if (document.getLegalTicket() == null || !ticketId.equals(document.getLegalTicket().getId())) {
            throw new ForbiddenException("Tai lieu khong thuoc yeu cau tu van nay");
        }
        if (!isCustomerVisible(document)) {
            throw new ForbiddenException("Tai lieu nay khong duoc chia se voi khach hang");
        }
        return loadResource(document);
    }

    private LegalTicket getOwnedTicket(String ticketId, Long customerId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay yeu cau tu van ID: " + ticketId));
        if (!ticket.getCreatedBy().getId().equals(customerId)) {
            throw new ForbiddenException("Ban khong co quyen truy cap file cua ticket nay");
        }
        return ticket;
    }

    private boolean isCustomerVisible(Document document) {
        return document.getVisibilityScope() == DocumentVisibilityScope.CUSTOMER;
    }

    private TicketFileResponse toResponse(Document doc) {
        return TicketFileResponse.builder()
                .documentId(doc.getId())
                .originalFileName(doc.getOriginalFileName())
                .storedFileName(doc.getStoredFileName())
                .filePath(null)
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .documentPurpose(doc.getDocumentPurpose())
                .visibilityScope(doc.getVisibilityScope())
                .uploadedAt(doc.getUploadedAt() != null ? doc.getUploadedAt() : doc.getUpdatedAt())
                .build();
    }

    private Resource loadResource(Document document) {
        try {
            Path filePath = Path.of(document.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("Khong the doc file tai lieu: " + document.getId());
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

        long maxBytes = maxFileSizeMb * 1024L * 1024L;
        long maxEncodedLength = ((maxBytes + 2L) / 3L) * 4L;
        if (normalized.length() > maxEncodedLength) {
            throw new ConflictException("TICKET_FILE_TOO_LARGE");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
            if (decoded.length > maxBytes) throw new ConflictException("TICKET_FILE_TOO_LARGE");
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Noi dung file base64 khong hop le", ex);
        }
    }

    private void validateFileContent(String fileName, String declaredMime, byte[] bytes) {
        String normalizedMime = declaredMime == null ? "" : declaredMime.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME_TYPES.contains(normalizedMime)) {
            throw new ConflictException("TICKET_FILE_MIME_NOT_ALLOWED");
        }
        String extension = extension(fileName);
        String detectedMime = detectMime(bytes, extension);
        if (!normalizedMime.equals(detectedMime)) {
            throw new ConflictException("TICKET_FILE_MIME_MISMATCH");
        }
    }

    private String detectMime(byte[] bytes, String extension) {
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (startsWith(bytes, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (startsWith(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return "image/jpeg";
        if (bytes.length >= 12 && startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        if ("docx".equals(extension) && isDocx(bytes)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ("txt".equals(extension) && isText(bytes)) return "text/plain";
        return "application/octet-stream";
    }

    private boolean isDocx(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) return true;
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private boolean isText(byte[] bytes) {
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned == 0 || (unsigned < 0x09) || (unsigned > 0x0D && unsigned < 0x20)) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private String extension(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
