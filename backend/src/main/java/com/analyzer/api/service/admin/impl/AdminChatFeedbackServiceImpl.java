package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.AiFeedbackSummaryResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.entity.ChatMessage;
import com.analyzer.api.entity.ChatMessageFeedback;
import com.analyzer.api.entity.User;
import com.analyzer.api.enums.*;
import com.analyzer.api.exception.validation.InvalidPageException;
import com.analyzer.api.exception.validation.InvalidSizeException;
import com.analyzer.api.repository.ChatMessageFeedbackRepository;
import com.analyzer.api.repository.ChatMessageRepository;
import com.analyzer.api.repository.ai.AiCitationRepository;
import com.analyzer.api.service.admin.AdminChatFeedbackService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminChatFeedbackServiceImpl implements AdminChatFeedbackService {
    private final ChatMessageFeedbackRepository feedbackRepository;
    private final ChatMessageRepository messageRepository;
    private final AiCitationRepository citationRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageFeedbackResponse> listFeedback(
            AiFeedbackType feedbackType, FeedbackRating legacyRating, ChatMode resolvedMode,
            RiskLevel riskLevel, LocalDate fromDate, LocalDate toDate, String keyword,
            int page, int size) {
        if (page < 0) throw new InvalidPageException("Page index must not be negative", page);
        if (size <= 0 || size > 200) throw new InvalidSizeException("Page size must be between 1 and 200", size);

        AiFeedbackType effectiveType = feedbackType != null ? feedbackType
                : legacyRating == FeedbackRating.THUMBS_UP ? AiFeedbackType.LIKE
                : legacyRating == FeedbackRating.THUMBS_DOWN ? AiFeedbackType.DISLIKE : null;
        Specification<ChatMessageFeedback> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveType != null) predicates.add(cb.equal(root.get("feedbackType"), effectiveType));
            if (resolvedMode != null) predicates.add(cb.equal(root.get("chatMessage").get("resolvedMode"), resolvedMode));
            if (riskLevel != null) predicates.add(cb.equal(root.get("chatMessage").get("riskLevel"), riskLevel));
            if (fromDate != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
            if (toDate != null) predicates.add(cb.lessThan(root.get("createdAt"), toDate.plusDays(1).atStartOfDay()));
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("chatMessage").get("content")), pattern),
                        cb.like(cb.lower(root.get("user").get("email")), pattern),
                        cb.like(cb.lower(root.get("reason")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ChatMessageFeedback> result = feedbackRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.<ChatMessageFeedbackResponse>builder()
                .items(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber()).size(result.getSize())
                .totalItems(result.getTotalElements()).totalPages(result.getTotalPages()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AiFeedbackSummaryResponse getSummary() {
        long total = feedbackRepository.count();
        long likes = feedbackRepository.countByFeedbackType(AiFeedbackType.LIKE);
        long dislikes = feedbackRepository.countByFeedbackType(AiFeedbackType.DISLIKE);
        return AiFeedbackSummaryResponse.builder()
                .total(total).likes(likes).dislikes(dislikes)
                .likeRate(total == 0 ? 0 : likes * 100.0 / total)
                .dislikeRate(total == 0 ? 0 : dislikes * 100.0 / total).build();
    }

    private ChatMessageFeedbackResponse toResponse(ChatMessageFeedback feedback) {
        ChatMessage answer = feedback.getChatMessage();
        User user = feedback.getUser() != null ? feedback.getUser() : answer.getUser();
        String question = messageRepository
                .findTopByChatSessionIdAndRoleAndCreatedAtBeforeOrderByCreatedAtDesc(
                        answer.getChatSession().getId(), ChatMessageRole.USER, answer.getCreatedAt())
                .map(ChatMessage::getContent).orElse(null);
        return ChatMessageFeedbackResponse.builder()
                .id(feedback.getId()).messageId(answer.getId()).chatMessageId(answer.getId())
                .chatSessionId(answer.getChatSession().getId())
                .feedbackType(feedback.getFeedbackType()).reason(feedback.getReason())
                .rating(feedback.getRating()).reasons(splitReasons(feedback.getReasons()))
                .comment(feedback.getComment()).messageContent(snippet(answer.getContent()))
                .answerSnippet(snippet(answer.getContent())).questionSnippet(snippet(question))
                .submittedById(user.getId()).submittedByName(user.getFirstName() + " " + user.getLastName())
                .userEmail(user.getEmail()).resolvedMode(answer.getResolvedMode() != null
                        ? answer.getResolvedMode()
                        : parseMode(answer.getChatSession().getConversationMode()))
                .riskLevel(answer.getRiskLevel()).sourceCount(citationRepository.countByChatMessage_Id(answer.getId()))
                .createdAt(feedback.getCreatedAt()).updatedAt(feedback.getUpdatedAt()).build();
    }

    private static ChatMode parseMode(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ChatMode.valueOf(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static String snippet(String value) {
        if (value == null || value.length() <= 240) return value;
        return value.substring(0, 240) + "...";
    }

    private static List<FeedbackReason> splitReasons(String reasons) {
        if (reasons == null || reasons.isBlank()) return List.of();
        return Arrays.stream(reasons.split(",")).map(FeedbackReason::valueOf).toList();
    }
}
