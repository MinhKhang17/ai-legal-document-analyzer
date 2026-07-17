package com.analyzer.api.service.admin;

import com.analyzer.api.dto.PageResponse;
import com.analyzer.api.dto.chatmessage.ChatMessageFeedbackResponse;

public interface AdminChatFeedbackService {

    PageResponse<ChatMessageFeedbackResponse> listFeedback(Integer rating, int page, int size);
}
