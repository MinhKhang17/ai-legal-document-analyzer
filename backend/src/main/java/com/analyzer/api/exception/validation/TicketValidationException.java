package com.analyzer.api.exception.validation;

import java.util.Map;

public class TicketValidationException extends RuntimeException {
    private final Map<String, String> fieldErrors;
    public TicketValidationException(Map<String, String> fieldErrors) {
        super("Ticket validation failed");
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
