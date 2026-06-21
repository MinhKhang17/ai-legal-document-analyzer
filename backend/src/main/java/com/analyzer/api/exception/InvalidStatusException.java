package com.analyzer.api.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class InvalidStatusException extends RuntimeException {
    private final List<String> allowedValues = List.of("ACTIVE", "DELETED");

    public InvalidStatusException(String message) {
        super(message);
    }
}
