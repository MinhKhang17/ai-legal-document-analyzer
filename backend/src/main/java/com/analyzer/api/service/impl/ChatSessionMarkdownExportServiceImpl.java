package com.analyzer.api.service.impl;

import com.analyzer.api.entity.AiCitation;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.Document;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.exception.common.ResourceNotFoundException;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.ChatSessionDocumentRepository;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.ChatSessionMarkdownExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionMarkdownExportServiceImpl implements ChatSessionMarkdownExportService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionDocumentRepository sessionDocumentRepository;
    private final AiCitationRepository citationRepository;

    @Override
    @Transactional(readOnly = true)
    public MarkdownExport export(Long userId, String sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CHAT_SESSION_NOT_FOUND"));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("CHAT_SESSION_ACCESS_DENIED");
        }

        List<ChatMessage> messages = chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(message -> message.getRole() != ChatMessageRole.SYSTEM)
                .filter(message -> message.getStatus() != ChatMessageStatus.FAILED)
                .toList();
        boolean hasAnalysis = messages.stream().anyMatch(message -> message.getRole() == ChatMessageRole.ASSISTANT
                && message.getStatus() == ChatMessageStatus.COMPLETED
                && message.getContent() != null && !message.getContent().isBlank());
        if (!hasAnalysis) {
            throw new ConflictException("CHAT_SESSION_HAS_NO_ANALYSIS_CONTENT");
        }

        List<Document> documents = sessionDocumentRepository
                .findByChatSessionIdAndUserIdOrderByAttachedAtAsc(sessionId, userId)
                .stream().map(mapping -> mapping.getDocument()).toList();
        String markdown = buildMarkdown(session, documents, messages);
        String baseName = documents.isEmpty() ? session.getTitle() : documents.get(0).getOriginalFileName();
        return new MarkdownExport(markdown.getBytes(StandardCharsets.UTF_8), safeFileName(baseName));
    }

    private String buildMarkdown(ChatSession session, List<Document> documents, List<ChatMessage> messages) {
        LocalDateTime analysisDate = messages.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .map(ChatMessage::getCreatedAt).filter(java.util.Objects::nonNull)
                .findFirst().orElse(session.getCreatedAt());
        StringBuilder output = new StringBuilder();
        output.append("# ").append(escapeInline(session.getTitle())).append("\n\n")
                .append("> Báo cáo do trợ lý AI hỗ trợ rà soát hợp đồng cá nhân đơn giản. ")
                .append("Nội dung không thay thế tư vấn của luật sư.\n\n")
                .append("- **Ngày phân tích:** ").append(DATE_TIME.format(analysisDate)).append("\n")
                .append("- **Phiên:** ").append(escapeInline(session.getId())).append("\n");

        if (!documents.isEmpty()) {
            output.append("\n## Tài liệu\n\n");
            for (Document document : documents) {
                output.append("- **").append(escapeInline(document.getOriginalFileName())).append("** — ")
                        .append(document.getContractType() == null ? "Chưa xác định" : document.getContractType().getDisplayName())
                        .append("\n");
            }
        }

        output.append("\n## Hội thoại và kết quả phân tích\n");
        for (ChatMessage message : messages) {
            if (message.getContent() == null || message.getContent().isBlank()) continue;
            if (message.getRole() == ChatMessageRole.USER) {
                output.append("\n### Câu hỏi — ").append(DATE_TIME.format(message.getCreatedAt())).append("\n\n")
                        .append(asBlockQuote(message.getContent())).append("\n");
            } else if (message.getRole() == ChatMessageRole.ASSISTANT) {
                output.append("\n### Phân tích AI — ").append(DATE_TIME.format(message.getCreatedAt())).append("\n\n")
                        .append(message.getContent().trim()).append("\n");
                appendMetadata(output, message);
                appendCitations(output, message);
            }
        }
        return output.toString();
    }

    private void appendMetadata(StringBuilder output, ChatMessage message) {
        if (message.getRiskLevel() != null) output.append("\n- **Mức rủi ro:** ").append(message.getRiskLevel()).append("\n");
        if (hasText(message.getLegalDomain())) output.append("- **Phạm vi:** ").append(escapeInline(message.getLegalDomain())).append("\n");
        if (message.getUserActionHint() != null) output.append("- **Khuyến nghị:** ").append(message.getUserActionHint()).append("\n");
        if (hasText(message.getSuggestionReason())) output.append("- **Lưu ý:** ").append(escapeInline(message.getSuggestionReason())).append("\n");
        if (hasText(message.getMissingInformation())) output.append("- **Thông tin còn thiếu:** ").append(escapeInline(message.getMissingInformation())).append("\n");
    }

    private void appendCitations(StringBuilder output, ChatMessage message) {
        List<AiCitation> citations = citationRepository.findByChatMessage_Id(message.getId());
        if (citations.isEmpty()) return;
        output.append("\n#### Căn cứ/tham chiếu\n\n");
        for (AiCitation citation : citations) {
            output.append("- ").append(escapeInline(citation.getLabel()));
            if (citation.getPageNumber() != null) output.append(" (trang ").append(citation.getPageNumber()).append(")");
            if (hasText(citation.getExcerpt())) output.append(": ").append(escapeInline(citation.getExcerpt()));
            output.append("\n");
        }
    }

    private String safeFileName(String source) {
        String stem = source == null || source.isBlank() ? "phan-tich-hop-dong" : source;
        stem = stem.replaceFirst("(?i)\\.[a-z0-9]{1,8}$", "")
                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "-")
                .replaceAll("\\.{2,}", "-").replaceAll("\\s+", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (stem.isBlank()) stem = "phan-tich-hop-dong";
        if (stem.length() > 80) stem = stem.substring(0, 80);
        return stem + "-" + LocalDate.now() + ".md";
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String escapeInline(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("`", "\\`").replace("\n", " ").replace("\r", " "); }
    private String asBlockQuote(String value) { return "> " + value.trim().replace("\r", "").replace("\n", "\n> "); }
}
