package com.analyzer.api.service.admin;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;
import com.analyzer.api.enums.FeedbackRating;

public interface AdminChatFeedbackService {

    PageResponse<ChatMessageFeedbackResponse> listFeedback(FeedbackRating rating, int page, int size);
}
