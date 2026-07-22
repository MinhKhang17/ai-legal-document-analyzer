package com.analyzer.api.service.impl;
import com.analyzer.api.service.chatmessage.impl.ChatMessageServiceImpl;

import com.analyzer.api.client.PythonAiClient;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackRequest;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.AiFeedbackType;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.exception.common.ForbiddenException;
import com.analyzer.api.repository.user.UserRepository;
import com.analyzer.api.repository.workspace.WorkspaceRepository;
import com.analyzer.api.repository.document.DocumentRepository;
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import com.analyzer.api.repository.chatmessage.ChatMessageFeedbackRepository;
import com.analyzer.api.repository.chatsession.ChatSessionRepository;
import com.analyzer.api.repository.chatsession.ChatSessionDocumentRepository;
import com.analyzer.api.repository.*;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.subscription.SubscriptionQuotaService;
import com.analyzer.api.service.conversation.ConversationHistoryAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageFeedbackAuthorizationTest {
    @Mock WorkspaceRepository workspaceRepository;
    @Mock DocumentRepository documentRepository;
    @Mock ChatSessionRepository chatSessionRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock PythonAiClient pythonAiClient;
    @Mock UserRepository userRepository;
    @Mock SubscriptionQuotaService subscriptionQuotaService;
    @Mock AiCitationRepository aiCitationRepository;
    @Mock ChatMessageFeedbackRepository feedbackRepository;
    @Mock ChatSessionDocumentRepository sessionDocumentRepository;
    @Mock ConversationHistoryAssembler historyAssembler;
    @InjectMocks ChatMessageServiceImpl service;

    @Test
    void cannotFeedbackAnotherUsersAssistantMessage() {
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message(9L, ChatMessageRole.ASSISTANT)));
        ChatMessageFeedbackRequest request = new ChatMessageFeedbackRequest();
        request.setFeedbackType(AiFeedbackType.LIKE);
        assertThrows(ForbiddenException.class, () -> service.submitFeedback(7L, "msg-1", request));
    }

    @Test
    void cannotFeedbackUserMessage() {
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message(7L, ChatMessageRole.USER)));
        ChatMessageFeedbackRequest request = new ChatMessageFeedbackRequest();
        request.setFeedbackType(AiFeedbackType.DISLIKE);
        assertThrows(ForbiddenException.class, () -> service.submitFeedback(7L, "msg-1", request));
    }

    @Test
    void likeIsStoredAgainstActualAssistantMessageAndUser() {
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message(7L, ChatMessageRole.ASSISTANT)));
        when(feedbackRepository.findByChatMessageIdAndUserId("msg-1", 7L)).thenReturn(Optional.empty());
        when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ChatMessageFeedbackRequest request = new ChatMessageFeedbackRequest();
        request.setFeedbackType(AiFeedbackType.LIKE);
        assertEquals(AiFeedbackType.LIKE, service.submitFeedback(7L, "msg-1", request).getFeedbackType());
    }

    private static ChatMessage message(Long ownerId, ChatMessageRole role) {
        User owner = User.builder().id(ownerId).firstName("Test").lastName("User").build();
        ChatSession session = ChatSession.builder().id("chat-1").user(owner).build();
        return ChatMessage.builder().id("msg-1").user(owner).chatSession(session).role(role).content("answer").build();
    }
}
