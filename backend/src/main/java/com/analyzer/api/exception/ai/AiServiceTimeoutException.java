package com.analyzer.api.exception.ai;

import lombok.Getter;

@Getter
public class AiServiceTimeoutException extends RuntimeException {
    private final String requestId;

    public AiServiceTimeoutException(String message, String requestId) {
        super(message);
        this.requestId = requestId;
    }
}
