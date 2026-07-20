package com.analyzer.api.exception.auth;

public class ExpiredVerificationTokenException extends RuntimeException {
    public ExpiredVerificationTokenException(String message) {
        super(message);
    }
}
