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

    /**
     * Sends an EXPERT account's temporary login credentials (created or reactivated by admin),
     * along with the password-change deadline. Fire-and-forget, same failure-handling contract
     * as {@link #sendVerificationEmailAsync}.
     */
    void sendExpertAccountCreatedEmailAsync(String toEmail, String recipientName, String temporaryPassword, int passwordChangeDeadlineDays);

    void sendTicketNotificationAsync(String toEmail, String recipientName, String ticketId,
                                     String ticketType, String ticketStatus, String relativePath,
                                     String detail);
}
