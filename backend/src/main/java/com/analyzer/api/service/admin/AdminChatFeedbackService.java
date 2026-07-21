package com.analyzer.api.service.admin;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.AiFeedbackSummaryResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.enums.AiFeedbackType;
import com.analyzer.api.enums.ChatMode;
import com.analyzer.api.enums.FeedbackRating;
import com.analyzer.api.enums.RiskLevel;

import java.time.LocalDate;

public interface AdminChatFeedbackService {
    PageResponse<ChatMessageFeedbackResponse> listFeedback(
            AiFeedbackType feedbackType,
            FeedbackRating legacyRating,
            ChatMode resolvedMode,
            RiskLevel riskLevel,
            LocalDate fromDate,
            LocalDate toDate,
            String keyword,
            int page,
            int size);

    AiFeedbackSummaryResponse getSummary();
}
