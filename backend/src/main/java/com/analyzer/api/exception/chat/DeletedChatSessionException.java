package com.analyzer.api.exception.chat;

import lombok.Getter;

@Getter
public class DeletedChatSessionException extends RuntimeException {
    private final String chatSessionId;
    private final String status;

    public DeletedChatSessionException(String message, String chatSessionId, String status) {
        super(message);
        this.chatSessionId = chatSessionId;
        this.status = status;
    }
}
