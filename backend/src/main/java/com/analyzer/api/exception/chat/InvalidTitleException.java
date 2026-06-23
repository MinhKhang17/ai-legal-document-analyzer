package com.analyzer.api.exception.chat;

import lombok.Getter;

@Getter
public class InvalidTitleException extends RuntimeException {
    private final boolean isBlank;
    private final int maxLength;

    public InvalidTitleException(String message, boolean isBlank, int maxLength) {
        super(message);
        this.isBlank = isBlank;
        this.maxLength = maxLength;
    }
}
