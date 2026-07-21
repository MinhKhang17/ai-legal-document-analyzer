package com.analyzer.api.service.impl;

import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.ChatSessionStatus;
import com.analyzer.api.enums.ShareAccessLevel;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.ChatSessionRepository;
import com.analyzer.api.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionSharingServiceImplTest {
    @Mock ChatSessionRepository sessionRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock ChatMessageRepository messageRepository;

    @Test
    void restrictedLinkRejectsAnonymousButAcceptsExpertSession() {
        ChatSession session = shared(ShareAccessLevel.RESTRICTED);
        when(sessionRepository.findByShareTokenAndIsSharedTrue("token")).thenReturn(Optional.of(session));
        ChatSessionServiceImpl service = new ChatSessionServiceImpl(sessionRepository, workspaceRepository, messageRepository);

        assertThrows(ForbiddenException.class, () -> service.getSharedChatSession("token", false));
        when(messageRepository.findByChatSessionIdOrderByCreatedAtAsc("chat_1")).thenReturn(List.of());
        assertTrue(service.getSharedChatSession("token", true).isReadOnly());
    }

    @Test
    void publicLinkAllowsAnonymousReadOnlyAccess() {
        ChatSession session = shared(ShareAccessLevel.PUBLIC);
        when(sessionRepository.findByShareTokenAndIsSharedTrue("token")).thenReturn(Optional.of(session));
        when(messageRepository.findByChatSessionIdOrderByCreatedAtAsc("chat_1")).thenReturn(List.of());
        ChatSessionServiceImpl service = new ChatSessionServiceImpl(sessionRepository, workspaceRepository, messageRepository);

        var result = service.getSharedChatSession("token", false);
        assertEquals(ShareAccessLevel.PUBLIC, result.getAccessLevel());
        assertTrue(result.isReadOnly());
    }

    private ChatSession shared(ShareAccessLevel level) {
        return ChatSession.builder().id("chat_1").title("Shared").status(ChatSessionStatus.ACTIVE)
                .user(User.builder().firstName("Test").lastName("User").build())
                .isShared(true).shareToken("token").shareAccessLevel(level)
                .createdAt(LocalDateTime.now()).sharedAt(LocalDateTime.now()).build();
    }
}
