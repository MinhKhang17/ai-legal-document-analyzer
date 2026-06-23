package com.analyzer.api.exception.workspace;

public class DocumentProcessingDispatchException extends RuntimeException {

    public DocumentProcessingDispatchException(String message) {
        super(message);
    }

    public DocumentProcessingDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
