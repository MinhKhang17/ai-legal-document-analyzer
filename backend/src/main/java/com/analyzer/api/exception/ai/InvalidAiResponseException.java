package com.analyzer.api.exception.ai;

public class InvalidAiResponseException extends RuntimeException {
    private final String errorCode;

    public InvalidAiResponseException(String message) {
        super(message);
        this.errorCode = "INVALID_AI_RESPONSE";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
