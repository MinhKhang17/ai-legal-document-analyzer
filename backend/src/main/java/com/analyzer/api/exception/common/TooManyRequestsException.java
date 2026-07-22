package com.analyzer.api.exception.common;

public class TooManyRequestsException extends RuntimeException {
    private final String errorCode;
    public TooManyRequestsException(String message) {
        super(message);
        this.errorCode = "TOO_MANY_REQUESTS";
    }
    public TooManyRequestsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}
