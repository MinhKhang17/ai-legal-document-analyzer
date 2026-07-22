package com.analyzer.api.service.conversation;

import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.enums.ChatMessageRole;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.enums.ChatMessageType;
import com.analyzer.api.repository.chatmessage.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationHistoryAssemblerTest {
    @Mock
    private ChatMessageRepository messageRepository;

    @Test
    void keepsEightRecentMessagesAndOnlySendsOlderMessagesAsIncrementalBatch() {
        ChatSession session = ChatSession.builder().id("chat_1").build();
        List<ChatMessage> ascending = messages(session, 10, 100);
        List<ChatMessage> descending = new ArrayList<>(ascending);
        java.util.Collections.reverse(descending);

        when(messageRepository.findByChatSessionIdAndStatusOrderByCreatedAtDesc(
                ArgumentMatchers.eq("chat_1"), ArgumentMatchers.eq(ChatMessageStatus.COMPLETED),
                ArgumentMatchers.any(Pageable.class))).thenReturn(descending);
        when(messageRepository.countByChatSessionIdAndStatus("chat_1", ChatMessageStatus.COMPLETED))
                .thenReturn(10L);
        when(messageRepository.findMessagesToSummarizeBefore(
                ArgumentMatchers.eq("chat_1"), ArgumentMatchers.eq(ChatMessageStatus.COMPLETED),
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.any(Pageable.class))).thenReturn(ascending.subList(0, 2));

        var window = new ConversationHistoryAssembler(messageRepository).build(session, List.of("doc_1"));

        assertThat(window.recentHistory()).hasSize(8);
        assertThat(window.recentHistory().get(0).getMessageId()).isEqualTo("msg_2");
        assertThat(window.evictedMessages()).extracting(message -> message.getMessageId())
                .containsExactly("msg_0", "msg_1");
    }

    @Test
    void usesTypedBetweenQueryWhenSummaryCursorExists() {
        ChatSession session = ChatSession.builder()
                .id("chat_3")
                .summaryThroughMessageId("msg_0")
                .build();
        List<ChatMessage> ascending = messages(session, 10, 100);
        List<ChatMessage> descending = new ArrayList<>(ascending);
        java.util.Collections.reverse(descending);

        when(messageRepository.findByChatSessionIdAndStatusOrderByCreatedAtDesc(
                ArgumentMatchers.eq("chat_3"), ArgumentMatchers.eq(ChatMessageStatus.COMPLETED),
                ArgumentMatchers.any(Pageable.class))).thenReturn(descending);
        when(messageRepository.countByChatSessionIdAndStatus("chat_3", ChatMessageStatus.COMPLETED))
                .thenReturn(10L);
        when(messageRepository.findById("msg_0")).thenReturn(java.util.Optional.of(ascending.get(0)));
        when(messageRepository.findMessagesToSummarizeBetween(
                ArgumentMatchers.eq("chat_3"), ArgumentMatchers.eq(ChatMessageStatus.COMPLETED),
                ArgumentMatchers.eq(ascending.get(0).getCreatedAt()),
                ArgumentMatchers.any(LocalDateTime.class), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(ascending.subList(1, 2));

        var window = new ConversationHistoryAssembler(messageRepository).build(session, List.of("doc_1"));

        assertThat(window.evictedMessages()).extracting(message -> message.getMessageId())
                .containsExactly("msg_1");
    }

    @Test
    void recentHistoryNeverExceedsApproximateThreeThousandTokenBudget() {
        ChatSession session = ChatSession.builder().id("chat_2").build();
        List<ChatMessage> ascending = messages(session, 2, 8_000);
        List<ChatMessage> descending = new ArrayList<>(ascending);
        java.util.Collections.reverse(descending);

        when(messageRepository.findByChatSessionIdAndStatusOrderByCreatedAtDesc(
                ArgumentMatchers.eq("chat_2"), ArgumentMatchers.eq(ChatMessageStatus.COMPLETED),
                ArgumentMatchers.any(Pageable.class))).thenReturn(descending);
        when(messageRepository.countByChatSessionIdAndStatus("chat_2", ChatMessageStatus.COMPLETED))
                .thenReturn(2L);

        var window = new ConversationHistoryAssembler(messageRepository).build(session, List.of("doc_1"));
        int approximateTokens = window.recentHistory().stream()
                .mapToInt(message -> (message.getContent().length() + 3) / 4)
                .sum();

        assertThat(window.recentHistory()).hasSize(2);
        assertThat(approximateTokens).isLessThanOrEqualTo(3_000);
    }

    private List<ChatMessage> messages(ChatSession session, int count, int contentLength) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(ChatMessage.builder()
                    .id("msg_" + index)
                    .chatSession(session)
                    .role(index % 2 == 0 ? ChatMessageRole.USER : ChatMessageRole.ASSISTANT)
                    .messageType(ChatMessageType.NORMAL_CHAT)
                    .status(ChatMessageStatus.COMPLETED)
                    .content("x".repeat(contentLength))
                    .createdAt(LocalDateTime.of(2026, 7, 18, 10, 0).plusMinutes(index))
                    .build());
        }
        return messages;
    }
}
