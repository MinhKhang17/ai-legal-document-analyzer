package com.analyzer.api.exception.ai;

import lombok.Getter;

@Getter
public class AiServiceUnavailableException extends RuntimeException {
    private final String requestId;

    public AiServiceUnavailableException(String message, String requestId) {
        super(message);
        this.requestId = requestId;
    }
}
