package com.analyzer.api.service;

public interface EmailService {

    /**
     * Sends the registration email-verification link. Fire-and-forget: failures are logged,
     * never thrown, so they cannot roll back the caller's transaction.
     */
    void sendVerificationEmailAsync(String toEmail, String recipientName, String token);

    /**
     * Notifies the document owner that AI ingestion finished successfully. Fire-and-forget,
     * same failure-handling contract as {@link #sendVerificationEmailAsync}.
     */
    void sendIngestionSuccessEmailAsync(String toEmail, String recipientName, String originalFileName);
}
