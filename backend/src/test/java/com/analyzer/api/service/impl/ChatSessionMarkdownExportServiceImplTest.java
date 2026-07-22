package com.analyzer.api.service.impl;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.enums.RiskLevel;
import com.analyzer.api.exception.common.ConflictException;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import com.analyzer.api.repository.chatsession.ChatSessionDocumentRepository;
import com.analyzer.api.repository.chatsession.ChatSessionRepository;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.ChatSessionMarkdownExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatSessionMarkdownExportServiceImplTest {
    private ChatSessionRepository sessionRepository;
    private ChatMessageRepository messageRepository;
    private ChatSessionDocumentRepository documentRepository;
    private AiCitationRepository citationRepository;
    private ChatSessionMarkdownExportService service;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ChatSessionRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        documentRepository = mock(ChatSessionDocumentRepository.class);
        citationRepository = mock(AiCitationRepository.class);
        service = new ChatSessionMarkdownExportServiceImpl(
                sessionRepository, messageRepository, documentRepository, citationRepository);
        session = ChatSession.builder()
                .id("chat_1").user(User.builder().id(10L).build())
                .title("Rà soát hợp đồng thuê nhà").status(ChatSessionStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 7, 19, 10, 0)).build();
        when(sessionRepository.findById("chat_1")).thenReturn(Optional.of(session));
        when(documentRepository.findByChatSessionIdAndUserIdOrderByAttachedAtAsc("chat_1", 10L))
                .thenReturn(List.of());
    }

    @Test
    void rejectsAnotherUsersSession() {
        assertThrows(ForbiddenException.class, () -> service.export(11L, "chat_1"));
        verifyNoInteractions(messageRepository);
    }

    @Test
    void rejectsSessionWithoutCompletedAnalysis() {
        when(messageRepository.findByChatSessionIdOrderByCreatedAtAsc("chat_1")).thenReturn(List.of(userMessage()));
        ConflictException exception = assertThrows(ConflictException.class, () -> service.export(10L, "chat_1"));
        assertEquals("CHAT_SESSION_HAS_NO_ANALYSIS_CONTENT", exception.getMessage());
    }

    @Test
    void exportsChronologicalUtf8MarkdownAndExcludesSystemAndTokenMetadata() {
        ChatMessage user = userMessage();
        ChatMessage assistant = ChatMessage.builder()
                .id("msg_2").chatSession(session).user(session.getUser())
                .role(ChatMessageRole.ASSISTANT).messageType(ChatMessageType.NORMAL_CHAT)
                .content("Điều khoản đặt cọc cần được xem xét.")
                .status(ChatMessageStatus.COMPLETED).riskLevel(RiskLevel.MEDIUM)
                .promptTokens(999).aiModel("hidden-model")
                .createdAt(LocalDateTime.of(2026, 7, 19, 10, 1)).build();
        ChatMessage system = ChatMessage.builder()
                .id("msg_system").chatSession(session).user(session.getUser())
                .role(ChatMessageRole.SYSTEM).messageType(ChatMessageType.NORMAL_CHAT)
                .content("INTERNAL SYSTEM PROMPT").status(ChatMessageStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2026, 7, 19, 9, 59)).build();
        when(messageRepository.findByChatSessionIdOrderByCreatedAtAsc("chat_1"))
                .thenReturn(List.of(system, user, assistant));
        when(citationRepository.findByChatMessage_Id("msg_2")).thenReturn(List.of());

        ChatSessionMarkdownExportService.MarkdownExport export = service.export(10L, "chat_1");
        String markdown = new String(export.content(), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("Rà soát hợp đồng thuê nhà"));
        assertTrue(markdown.indexOf("Tiền cọc là bao nhiêu?") < markdown.indexOf("Điều khoản đặt cọc"));
        assertTrue(markdown.contains("Mức rủi ro:** MEDIUM"));
        assertFalse(markdown.contains("INTERNAL SYSTEM PROMPT"));
        assertFalse(markdown.contains("hidden-model"));
        assertFalse(markdown.contains("999"));
        assertTrue(export.fileName().startsWith("Rà-soát-hợp-đồng-thuê-nhà-"));
        assertTrue(export.fileName().endsWith(".md"));
    }

    private ChatMessage userMessage() {
        return ChatMessage.builder()
                .id("msg_1").chatSession(session).user(session.getUser())
                .role(ChatMessageRole.USER).messageType(ChatMessageType.NORMAL_CHAT)
                .content("Tiền cọc là bao nhiêu?").status(ChatMessageStatus.COMPLETED)
                .createdAt(LocalDateTime.of(2026, 7, 19, 10, 0)).build();
    }
}
