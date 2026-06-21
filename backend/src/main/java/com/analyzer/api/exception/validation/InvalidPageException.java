package com.analyzer.api.exception.validation;

import lombok.Getter;

@Getter
public class InvalidPageException extends RuntimeException {
    private final int page;

    public InvalidPageException(String message, int page) {
        super(message);
        this.page = page;
    }
}
