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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketFileServiceImpl implements TicketFileService {

    private final LegalTicketRepository legalTicketRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TicketFileResponse> listFiles(String ticketId, Long lawyerId) {
        LegalTicket ticket = legalTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Bạn không phải là Luật sư được phân công xử lý yêu cầu này");
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Yêu cầu tư vấn ID: " + ticketId));

        if (ticket.getAssignedLawyer() == null || !ticket.getAssignedLawyer().getId().equals(lawyerId)) {
            throw new ForbiddenException("Bạn không phải là Luật sư được phân công xử lý yêu cầu này");
        }

        User lawyer = userRepository.findById(lawyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Luật sư ID: " + lawyerId));

        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        DocumentVisibilityScope visibility = request.getVisibilityScope() != null ? request.getVisibilityScope() : DocumentVisibilityScope.ALL_INTERNAL;

        Document document = Document.builder()
                .id(docId)
                .workspace(ticket.getWorkspace())
                .user(lawyer)
                .legalTicket(ticket)
                .originalFileName(request.getOriginalFileName())
                .storedFileName(docId + "_" + request.getOriginalFileName())
                .filePath("/uploads/tickets/" + docId)
                .fileType(request.getFileType())
                .fileSize(0L)
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
}
