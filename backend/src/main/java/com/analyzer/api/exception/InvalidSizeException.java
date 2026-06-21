package com.analyzer.api.exception;

import lombok.Getter;

@Getter
public class InvalidSizeException extends RuntimeException {
    private final int size;

    public InvalidSizeException(String message, int size) {
        super(message);
        this.size = size;
    }
}
