package com.analyzer.api.service.conversation;

import com.analyzer.api.dto.ai.RagQueryRequest;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatSession;
import com.analyzer.api.enums.ChatMessageStatus;
import com.analyzer.api.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConversationHistoryAssembler {
    private static final int RECENT_MESSAGE_LIMIT = 8;
    private static final int RECENT_TOKEN_LIMIT = 3_000;
    private static final int SUMMARY_BATCH_MESSAGE_LIMIT = 12;
    private static final int SUMMARY_BATCH_TOKEN_LIMIT = 4_000;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[((?:KB|USER)-\\d+)]", Pattern.CASE_INSENSITIVE);

    private final ChatMessageRepository messageRepository;

    public MemoryWindow build(ChatSession session, List<String> activeDocumentIds) {
        List<ChatMessage> newest = messageRepository.findByChatSessionIdAndStatusOrderByCreatedAtDesc(
                session.getId(), ChatMessageStatus.COMPLETED, PageRequest.of(0, 16));
        List<ChatMessage> selectedDescending = selectRecentPairs(newest);
        List<ChatMessage> selectedAscending = new ArrayList<>(selectedDescending);
        Collections.reverse(selectedAscending);

        List<RagQueryRequest.ConversationMessage> recent = toRecentPayloads(selectedAscending, activeDocumentIds);

        long completedCount = messageRepository.countByChatSessionIdAndStatus(session.getId(), ChatMessageStatus.COMPLETED);
        List<RagQueryRequest.ConversationMessage> evicted = List.of();
        if (completedCount > selectedDescending.size() && !selectedAscending.isEmpty()) {
            LocalDateTime after = resolveSummaryCursor(session);
            LocalDateTime before = selectedAscending.get(0).getCreatedAt();
            PageRequest batchPage = PageRequest.of(0, SUMMARY_BATCH_MESSAGE_LIMIT);
            List<ChatMessage> candidates = after == null
                    ? messageRepository.findMessagesToSummarizeBefore(
                            session.getId(), ChatMessageStatus.COMPLETED, before, batchPage)
                    : messageRepository.findMessagesToSummarizeBetween(
                            session.getId(), ChatMessageStatus.COMPLETED, after, before, batchPage);
            evicted = limitByTokens(candidates, activeDocumentIds, SUMMARY_BATCH_TOKEN_LIMIT);
        }
        return new MemoryWindow(recent, evicted);
    }

    private List<ChatMessage> selectRecentPairs(List<ChatMessage> newest) {
        List<ChatMessage> selected = new ArrayList<>();
        int tokens = 0;
        for (int index = 0; index < newest.size() && selected.size() < RECENT_MESSAGE_LIMIT;) {
            List<ChatMessage> pair = new ArrayList<>();
            pair.add(newest.get(index++));
            if (index < newest.size() && selected.size() + pair.size() < RECENT_MESSAGE_LIMIT) {
                pair.add(newest.get(index++));
            }
            int pairTokens = pair.stream().mapToInt(message -> estimateTokens(message.getContent())).sum();
            if (!selected.isEmpty() && tokens + pairTokens > RECENT_TOKEN_LIMIT) break;
            selected.addAll(pair);
            tokens += Math.min(pairTokens, RECENT_TOKEN_LIMIT - tokens);
        }
        return selected;
    }

    private List<RagQueryRequest.ConversationMessage> limitByTokens(
            List<ChatMessage> messages, List<String> activeDocumentIds, int tokenLimit) {
        List<RagQueryRequest.ConversationMessage> result = new ArrayList<>();
        int used = 0;
        for (ChatMessage message : messages) {
            int remaining = tokenLimit - used;
            if (remaining <= 0) break;
            result.add(toPayload(message, activeDocumentIds, remaining));
            used += Math.min(estimateTokens(message.getContent()), remaining);
        }
        return result;
    }

    private List<RagQueryRequest.ConversationMessage> toRecentPayloads(
            List<ChatMessage> messages, List<String> activeDocumentIds) {
        List<RagQueryRequest.ConversationMessage> result = new ArrayList<>();
        int remaining = RECENT_TOKEN_LIMIT;
        for (int index = 0; index < messages.size() && remaining > 0; index += 2) {
            List<ChatMessage> pair = messages.subList(index, Math.min(index + 2, messages.size()));
            int pairTokens = pair.stream().mapToInt(message -> estimateTokens(message.getContent())).sum();
            if (pairTokens <= remaining) {
                for (ChatMessage message : pair) result.add(toPayload(message, activeDocumentIds, remaining));
                remaining -= pairTokens;
                continue;
            }
            int perMessageBudget = Math.max(1, remaining / pair.size());
            for (ChatMessage message : pair) result.add(toPayload(message, activeDocumentIds, perMessageBudget));
            remaining = 0;
        }
        return result;
    }

    private RagQueryRequest.ConversationMessage toPayload(
            ChatMessage message, List<String> activeDocumentIds, int tokenLimit) {
        String content = truncateToTokens(message.getContent(), tokenLimit);
        return RagQueryRequest.ConversationMessage.builder()
                .messageId(message.getId())
                .role(message.getRole().name())
                .content(content)
                .createdAt(message.getCreatedAt().toString())
                .documentIds(activeDocumentIds)
                .citationIds(extractCitationIds(message.getContent()))
                .build();
    }

    private LocalDateTime resolveSummaryCursor(ChatSession session) {
        if (session.getSummaryThroughMessageId() == null) return null;
        return messageRepository.findById(session.getSummaryThroughMessageId())
                .filter(message -> message.getChatSession().getId().equals(session.getId()))
                .map(ChatMessage::getCreatedAt)
                .orElse(null);
    }

    private List<String> extractCitationIds(String content) {
        if (content == null) return List.of();
        List<String> ids = new ArrayList<>();
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1).toUpperCase();
            if (!ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private int estimateTokens(String content) {
        return content == null || content.isBlank() ? 0 : Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }

    private String truncateToTokens(String content, int tokenLimit) {
        if (content == null) return "";
        int maxChars = Math.max(0, tokenLimit * 4);
        return content.length() <= maxChars ? content : content.substring(0, maxChars);
    }

    public record MemoryWindow(
            List<RagQueryRequest.ConversationMessage> recentHistory,
            List<RagQueryRequest.ConversationMessage> evictedMessages) {
    }
}
