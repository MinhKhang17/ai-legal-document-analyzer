package com.analyzer.api.exception;

public class DocumentProcessingDispatchException extends RuntimeException {

    public DocumentProcessingDispatchException(String message) {
        super(message);
    }

    public DocumentProcessingDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
