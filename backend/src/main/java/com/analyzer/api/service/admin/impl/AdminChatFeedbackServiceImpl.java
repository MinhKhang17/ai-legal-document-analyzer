package com.analyzer.api.service.admin.impl;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.entity.ChatMessageFeedback;
import com.analyzer.api.exception.validation.InvalidPageException;
import com.analyzer.api.exception.validation.InvalidSizeException;
import com.analyzer.api.repository.ChatMessageFeedbackRepository;
import com.analyzer.api.service.admin.AdminChatFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminChatFeedbackServiceImpl implements AdminChatFeedbackService {

    private final ChatMessageFeedbackRepository chatMessageFeedbackRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageFeedbackResponse> listFeedback(Integer rating, int page, int size) {
        if (page < 0) {
            throw new InvalidPageException("Page index must not be negative", page);
        }
        if (size <= 0) {
            throw new InvalidSizeException("Page size must be greater than 0", size);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessageFeedback> feedbackPage = rating != null
                ? chatMessageFeedbackRepository.findByRating(rating, pageable)
                : chatMessageFeedbackRepository.findAll(pageable);

        List<ChatMessageFeedbackResponse> items = feedbackPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PageResponse.<ChatMessageFeedbackResponse>builder()
                .items(items)
                .page(feedbackPage.getNumber())
                .size(feedbackPage.getSize())
                .totalItems(feedbackPage.getTotalElements())
                .totalPages(feedbackPage.getTotalPages())
                .build();
    }

    private ChatMessageFeedbackResponse toResponse(ChatMessageFeedback feedback) {
        String content = feedback.getChatMessage().getContent();
        return ChatMessageFeedbackResponse.builder()
                .id(feedback.getId())
                .chatMessageId(feedback.getChatMessage().getId())
                .messageContent(content != null && content.length() > 200 ? content.substring(0, 200) + "..." : content)
                .rating(feedback.getRating())
                .comment(feedback.getComment())
                .createdAt(feedback.getCreatedAt())
                .build();
    }
}
