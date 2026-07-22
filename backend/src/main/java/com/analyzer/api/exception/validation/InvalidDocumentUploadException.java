package com.analyzer.api.exception.validation;

public class InvalidDocumentUploadException extends RuntimeException {
    private final String errorCode;

    public InvalidDocumentUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
